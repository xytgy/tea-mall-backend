package com.xytgy.teamallbackend.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xytgy.teamallbackend.annotation.BrowserCache;
import com.xytgy.teamallbackend.annotation.CacheStrategy;
import com.xytgy.teamallbackend.cache.browser.LastModifiedProvider;
import com.xytgy.teamallbackend.cache.browser.NoLastModifiedProvider;
import com.xytgy.teamallbackend.properties.BrowserCacheProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.HttpHeaders;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import com.xytgy.teamallbackend.utils.DigestUtils;

/**
 * L3 浏览器缓存 AOP 切面。
 * <p>
 * 拦截标注了 {@link BrowserCache} 注解的 Controller 方法，
 * 根据注解配置设置 HTTP 缓存响应头，并处理协商缓存。
 *
 * <h3>什么是浏览器缓存？</h3>
 * <p>
 * 浏览器缓存的核心思想是：如果资源没变，就不需要重新传输。
 * 有两种机制：
 * <ul>
 *   <li><b>强缓存（Strong Cache）</b>：通过 {@code Cache-Control} 的 {@code max-age} 指令，
 *       浏览器在有效期内直接使用本地缓存，不发请求给服务端。</li>
 *   <li><b>协商缓存（Negotiated Cache）</b>：浏览器每次发请求询问服务端资源是否变了。
 *       如果没变，服务端返回 {@code 304 Not Modified}（不含响应体），浏览器用本地缓存；
 *       如果变了，返回 {@code 200 + 新内容}。
 *       协商缓存有两种判断方式：
 *       <ul>
 *         <li>{@code Last-Modified / If-Modified-Since}：按时间判断，精度秒级</li>
 *         <li>{@code ETag / If-None-Match}：按内容哈希判断，精度到字节</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <h3>与 {@link com.xytgy.teamallbackend.filter.CacheHeaderFilter} 的关系</h3>
 * <p>
 * 两者都负责设置缓存头，但触发方式不同：
 * <ul>
 *   <li><b>注解驱动（此切面）</b>：精确到 Controller 方法级别，开发者显式声明缓存策略，优先级更高</li>
 *   <li><b>路径规则驱动（过滤器）</b>：按 URL 前缀批量处理，覆盖未标注注解的接口</li>
 * </ul>
 * <p>
 * 当一个方法上同时有 {@code @BrowserCache} 注解且 URL 匹配过滤器规则时，切面先生效（AOP 优先级高于 Filter）。
 *
 * <h3>切面执行流程</h3>
 * <ol>
 *   <li>前置守卫：检查全局开关、请求上下文、注解是否存在，不满足则直接放行</li>
 *   <li>构建并设置 {@code Cache-Control} 响应头（强缓存策略）</li>
 *   <li>构建并设置 {@code Vary} 响应头（缓存版本控制）</li>
 *   <li>如果是 {@code noStore} 模式，直接执行目标方法并返回（不处理协商缓存）</li>
 *   <li>通过配置的 Provider 获取资源真实更新时间，并设置 {@code Last-Modified}</li>
 *   <li>检查 {@code If-Modified-Since} 请求头（时间协商缓存），命中则返回 304</li>
 *   <li>执行目标方法获取响应体</li>
 *   <li>根据响应体生成 ETag（MD5 哈希），设置 {@code ETag} 响应头</li>
 *   <li>检查 {@code If-None-Match} 请求头（内容协商缓存），命中则返回 304</li>
 * </ol>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class BrowserCacheAspect {

    /** 缓存 Method -> BrowserCache 注解映射，避免每次请求走反射 */
    private static final ConcurrentHashMap<Method, BrowserCache> ANNOTATION_CACHE = new ConcurrentHashMap<>();

    private final BrowserCacheProperties properties;
    private final ObjectMapper objectMapper;
    private final ApplicationContext applicationContext;

    /** HTTP 日期格式：RFC 1123 */
    private static final DateTimeFormatter HTTP_DATE_FORMATTER =
            DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC);

    /**
     * 环绕通知：拦截所有标注了 {@link BrowserCache} 注解的 Controller 方法。
     * <p>
     * 在目标方法执行前后插入缓存逻辑：
     * <ul>
     *   <li>执行前：设置 Cache-Control、Vary，并在 Provider 可用时设置 Last-Modified，
     *       检查 If-Modified-Since 协商缓存（可能直接返回 304，不执行业务方法）</li>
     *   <li>执行后：根据响应体生成 ETag，检查 If-None-Match 协商缓存</li>
     * </ul>
     *
     * @param joinPoint AOP 连接点，代表被拦截的目标方法
     * @return 目标方法的返回值；协商缓存命中时返回 null（304 不需要响应体）
     * @throws Throwable 目标方法抛出的异常原样抛出
     */
    @Around("@annotation(com.xytgy.teamallbackend.annotation.BrowserCache)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 浏览器缓存层未启用时直接放行
        if (!properties.isEnabled()) {
            return joinPoint.proceed();
        }

        // 获取当前请求和响应
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = attributes.getResponse();
        if (response == null) {
            return joinPoint.proceed();
        }

        // 从方法签名中提取注解
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        BrowserCache annotation = ANNOTATION_CACHE.computeIfAbsent(method, m -> m.getAnnotation(BrowserCache.class));
        if (annotation == null) {
            return joinPoint.proceed();
        }

        // 标记此请求已由 Aspect 处理，避免 Filter 重复设置缓存头
        request.setAttribute("BROWSER_CACHE_ASPECT_HANDLED", true);

        // 1. 构建 Cache-Control 头值
        String cacheControl = buildCacheControl(annotation);
        response.setHeader(HttpHeaders.CACHE_CONTROL, cacheControl);

        // 2. 设置 Vary 头
        String vary = buildVary(annotation);
        if (vary != null) {
            response.setHeader(HttpHeaders.VARY, vary);
        }

        // 3. no-store 模式：直接执行，不做 ETag/Last-Modified 处理
        if (annotation.noStore()) {
            return joinPoint.proceed();
        }

        // 4. 使用资源真实更新时间处理 Last-Modified 协商缓存
        Optional<Instant> lastModified = resolveLastModified(annotation, method, joinPoint.getArgs());
        if (lastModified.isPresent()) {
            response.setHeader(HttpHeaders.LAST_MODIFIED,
                    HTTP_DATE_FORMATTER.format(lastModified.get()));
            if (handleIfModifiedSince(request, response, lastModified.get())) {
                return null;
            }
        }

        // 5. 执行目标方法
        Object result = joinPoint.proceed();

        // 6. 生成 ETag 并检查 If-None-Match
        if (handleEtag(annotation, request, response, result)) return null;

        return result;
    }

    /**
     * 检查基于时间的协商缓存（If-Modified-Since）。
     * <p>
     * 工作原理：
     * <ol>
     *   <li>服务端在第一次响应时设置 {@code Last-Modified} 头，值为资源最后修改时间</li>
     *   <li>浏览器下次请求时将该时间放在 {@code If-Modified-Since} 请求头中</li>
     *   <li>服务端比较两个时间，如果资源未修改（精度到秒），返回 304 不传响应体</li>
     * </ol>
     * <p>
     * 局限性：HTTP 日期精度只到秒级，一秒内多次修改可能检测不到。
     * 因此通常与 ETag 配合使用，先比时间（成本低），再比内容哈希（成本高）。
     *
     * @param request    当前 HTTP 请求
     * @param response   当前 HTTP 响应
     * @param lastModified 资源真实的最后修改时间
     * @return true 表示资源未修改，已设置 304 响应，调用方应直接返回 null
     */
    private boolean handleIfModifiedSince(HttpServletRequest request,
                                          HttpServletResponse response,
                                          Instant lastModified) {
        if (!properties.isEnableLastModified()) {
            return false;
        }
        String ifModifiedSince = request.getHeader(HttpHeaders.IF_MODIFIED_SINCE);
        if (ifModifiedSince == null) {
            return false;
        }
        try {
            long clientTime = HTTP_DATE_FORMATTER.parse(ifModifiedSince, Instant::from).toEpochMilli();
            if (lastModified.toEpochMilli() / 1000 <= clientTime / 1000) {
                response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                return true;
            }
        } catch (Exception e) {
            log.debug("解析 If-Modified-Since 头失败: {}", ifModifiedSince);
        }
        return false;
    }

    private Optional<Instant> resolveLastModified(BrowserCache annotation,
                                                  Method method,
                                                  Object[] args) {
        if (!properties.isEnableLastModified()
                || annotation.lastModifiedProvider() == NoLastModifiedProvider.class) {
            return Optional.empty();
        }
        try {
            LastModifiedProvider provider =
                    applicationContext.getBean(annotation.lastModifiedProvider());
            return provider.getLastModified(method, args);
        } catch (Exception e) {
            log.warn("查询资源最后修改时间失败，降级使用 ETag: method={}, error={}",
                    method.getName(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 生成 ETag 并检查基于内容的协商缓存（If-None-Match）。
     * <p>
     * 工作原理：
     * <ol>
     *   <li>服务端将响应体序列化为 JSON 并计算 MD5 哈希，作为 ETag 设置到响应头</li>
     *   <li>浏览器下次请求时将 ETag 值放在 {@code If-None-Match} 请求头中</li>
     *   <li>服务端重新计算响应体的 ETag，与客户端发来的值比较：
     *       匹配则返回 304（内容没变），不匹配则返回 200 + 新内容</li>
     * </ol>
     * <p>
     * 与 Last-Modified 的区别：ETag 是按内容哈希判断，精确到字节级，不受时间精度限制。
     * <p>
     * 注意：使用 {@link ObjectMapper#writeValueAsString} 序列化响应体生成 ETag，
     * 确保同一对象总是产生相同的 JSON 字符串，避免使用 {@code toString()} 导致 ETag 不稳定。
     *
     * @param annotation {@link BrowserCache} 注解实例，控制是否启用 ETag
     * @param request    当前 HTTP 请求
     * @param response   当前 HTTP 响应
     * @param result     目标方法的返回值（响应体），null 时不生成 ETag
     * @return true 表示 ETag 匹配，已设置 304 响应，调用方应直接返回 null
     */
    private boolean handleEtag(BrowserCache annotation,
                               HttpServletRequest request,
                               HttpServletResponse response,
                               Object result) {
        if (!annotation.etag() || !properties.isEnableEtag() || result == null) {
            return false;
        }
        String bodyJson;
        try {
            bodyJson = objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("序列化响应体生成 ETag 失败，跳过 ETag: {}", e.getMessage());
            return false;
        }
        String etag = DigestUtils.md5Hex(bodyJson.getBytes(StandardCharsets.UTF_8));
        response.setHeader(HttpHeaders.ETAG, "\"" + etag + "\"");

        String ifNoneMatch = request.getHeader(HttpHeaders.IF_NONE_MATCH);
        if (ifNoneMatch != null && ifNoneMatch.contains(etag)) {
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return true;
        }
        return false;
    }

    /**
     * 根据 {@link BrowserCache} 注解属性构建 {@code Cache-Control} 响应头值。
     * <p>
     * {@code Cache-Control} 是 HTTP 强缓存的核心头，告诉浏览器在多长时间内可以直接使用本地缓存。
     * 常见指令：
     * <ul>
     *   <li>{@code public}：任何缓存（浏览器、CDN）都可以缓存</li>
     *   <li>{@code private}：只有浏览器可以缓存，CDN 不能</li>
     *   <li>{@code max-age=秒}：浏览器缓存有效期</li>
     *   <li>{@code s-maxage=秒}：CDN 缓存有效期（优先级高于 max-age）</li>
     *   <li>{@code no-cache}：每次都协商验证（不是不缓存）</li>
     *   <li>{@code no-store}：完全不缓存（支付、认证等敏感场景）</li>
     * </ul>
     * <p>
     * 优先级：{@code noStore} > 自定义 {@code maxAge/sMaxAge} > 策略默认值。
     *
     * @param annotation {@link BrowserCache} 注解实例
     * @return 完整的 Cache-Control 头值字符串，如 {@code "public, max-age=60, s-maxage=30"}
     */
    private String buildCacheControl(BrowserCache annotation) {
        // 1. noStore 优先
        if (annotation.noStore()) {
            return "no-store";
        }

        CacheStrategy strategy = annotation.strategy();

        // 2. 如果策略是 no-store，直接返回
        if (strategy.isNoStore()) {
            return "no-store";
        }

        // 3. no-cache 模式
        if (strategy.isNoCache()) {
            return strategy.getVisibility() != null
                    ? strategy.getVisibility() + ", no-cache"
                    : "no-cache";
        }

        // 4. 确定可见性
        String visibility = strategy.getVisibility() != null ? strategy.getVisibility() : "public";

        // 5. 确定 max-age：注解覆盖 > 策略默认
        int maxAge = annotation.maxAge() > 0 ? annotation.maxAge() : strategy.getDefaultMaxAge();

        // 6. 确定 s-maxage：注解覆盖 > 策略默认
        int sMaxAge = annotation.sMaxAge() > 0 ? annotation.sMaxAge() : strategy.getDefaultSMaxAge();

        // 7. 拼接
        StringBuilder sb = new StringBuilder();
        sb.append(visibility).append(", max-age=").append(maxAge);
        if (sMaxAge > 0) {
            sb.append(", s-maxage=").append(sMaxAge);
        }
        return sb.toString();
    }

    /**
     * 根据注解属性构建 {@code Vary} 响应头值。
     * <p>
     * {@code Vary} 告诉缓存服务器（浏览器、CDN）：根据哪些请求头来区分缓存版本。
     * 例如 {@code Vary: Accept-Encoding} 表示 gzip 和非 gzip 响应要分开缓存。
     * 常用值：{@code Accept-Encoding}、{@code Accept-Language}、{@code Authorization} 等。
     *
     * @param annotation {@link BrowserCache} 注解实例
     * @return Vary 头值，多个值逗号分隔；未配置时返回 null（不设置 Vary 头）
     */
    private String buildVary(BrowserCache annotation) {
        String[] vary = annotation.vary();
        if (vary == null || vary.length == 0) {
            return null;
        }
        return String.join(", ", vary);
    }

}
