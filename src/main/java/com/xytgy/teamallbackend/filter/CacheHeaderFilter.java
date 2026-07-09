package com.xytgy.teamallbackend.filter;

import com.xytgy.teamallbackend.properties.BrowserCacheProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.Map;

import com.xytgy.teamallbackend.utils.DigestUtils;

/**
 * L3 浏览器缓存响应头过滤器。
 * <p>
 * 作为 OncePerRequestFilter 执行，在响应返回前根据配置规则设置缓存相关 HTTP 头：
 * <ul>
 *   <li><b>Cache-Control</b>：根据路径前缀匹配规则设置</li>
 *   <li><b>ETag</b>：基于响应体内容的 MD5 哈希生成</li>
 *   <li><b>ETag / If-None-Match</b>：基于响应内容进行协商缓存校验</li>
 * </ul>
 * <p>
 * 此过滤器处理基于 URL 路径规则的缓存头设置，与
 * {@link com.xytgy.teamallbackend.aspect.BrowserCacheAspect} 注解驱动的缓存互补。
 * <p>
 * 优先级：注解驱动 > 路径规则匹配。当方法上标注了 {@code @BrowserCache} 注解时，
 * 由切面处理；未标注时由此过滤器根据路径规则处理。
 *
 * @see BrowserCacheProperties
 * @see com.xytgy.teamallbackend.aspect.BrowserCacheAspect
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
@RequiredArgsConstructor
public class CacheHeaderFilter extends OncePerRequestFilter {

    private final BrowserCacheProperties properties;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        // 浏览器缓存层未启用时跳过
        if (!properties.isEnabled()) {
            return true;
        }
        // Aspect 已处理的请求不再由 Filter 重复处理
        if (request.getAttribute("BROWSER_CACHE_ASPECT_HANDLED") != null) {
            return true;
        }
        String uri = request.getRequestURI();
        // 仅处理 API 路径和静态资源
        return !uri.startsWith("/api/") && !isStaticResource(uri);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();

        // 1. 检查是否为敏感路径（no-store）
        if (isNoStore(uri)) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            filterChain.doFilter(request, response);
            return;
        }

        // 2. 查找匹配的缓存规则
        String cacheControl = resolveCacheControl(uri);

        // 3. 如果是静态资源，使用静态资源策略
        if (isStaticResource(uri)) {
            cacheControl = "public, max-age=" + properties.getStaticMaxAge();
        }

        // 4. 动态路径没有可靠的资源更新时间，不生成虚假的 Last-Modified。
        // 真实时间由 @BrowserCache 配置的 LastModifiedProvider 提供。

        // 5. 包装响应以支持读取响应体（用于 ETag 生成）
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        // 6. 执行后续过滤器链
        filterChain.doFilter(request, wrappedResponse);

        // Controller 方法上的注解已处理缓存，Filter 不再覆盖响应头
        if (request.getAttribute("BROWSER_CACHE_ASPECT_HANDLED") != null) {
            wrappedResponse.copyBodyToResponse();
            return;
        }

        // 7. 设置 Cache-Control 头
        if (cacheControl != null) {
            wrappedResponse.setHeader(HttpHeaders.CACHE_CONTROL, cacheControl);
        }

        // 8. 生成并设置 ETag
        if (properties.isEnableEtag() && shouldGenerateEtag(request.getMethod())) {
            byte[] body = wrappedResponse.getContentAsByteArray();
            if (body.length > 0) {
                String etag = DigestUtils.md5Hex(body);
                wrappedResponse.setHeader(HttpHeaders.ETAG, "\"" + etag + "\"");

                // 协商缓存：检查 If-None-Match
                String ifNoneMatch = request.getHeader(HttpHeaders.IF_NONE_MATCH);
                if (ifNoneMatch != null && ifNoneMatch.contains(etag)) {
                    wrappedResponse.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    wrappedResponse.setContentLength(0);
                    wrappedResponse.copyBodyToResponse();
                    return;
                }
            }
        }

        // 9. 将响应体写回客户端
        wrappedResponse.copyBodyToResponse();
    }

    /**
     * 按最长前缀匹配查找缓存规则。
     *
     * @param uri 请求 URI
     * @return 匹配的 Cache-Control 值，未匹配时返回默认策略
     */
    private String resolveCacheControl(String uri) {
        Map<String, String> rules = properties.getRules();
        String bestMatch = null;
        int bestMatchLength = 0;

        for (Map.Entry<String, String> entry : rules.entrySet()) {
            String prefix = entry.getKey();
            if (uri.startsWith(prefix) && prefix.length() > bestMatchLength) {
                bestMatch = entry.getValue();
                bestMatchLength = prefix.length();
            }
        }

        return bestMatch != null ? bestMatch : properties.getDefaultCacheControl();
    }

    /**
     * 判断路径是否为敏感数据（不应缓存）。
     */
    private boolean isNoStore(String uri) {
        for (String prefix : properties.getNoStorePrefixes()) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为静态资源路径。
     */
    private boolean isStaticResource(String uri) {
        return uri.matches(".*\\.(js|css|png|jpg|jpeg|gif|svg|ico|woff2?|ttf|eot|map)$");
    }

    /**
     * 仅对 GET/HEAD 请求生成 ETag（POST 等有副作用的请求不应缓存）。
     */
    private boolean shouldGenerateEtag(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }

}
