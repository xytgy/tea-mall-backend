package com.xytgy.teamallbackend.annotation;

import com.xytgy.teamallbackend.cache.browser.LastModifiedProvider;
import com.xytgy.teamallbackend.cache.browser.NoLastModifiedProvider;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 浏览器缓存注解，标注在 Controller 方法上，声明该接口的浏览器缓存策略。
 * <p>
 * 由 {@link com.xytgy.teamallbackend.aspect.BrowserCacheAspect} 切面拦截处理，
 * 根据注解属性设置对应的 HTTP 缓存响应头（Cache-Control、ETag、Last-Modified 等）。
 * <p>
 * 使用示例：
 * <pre>
 * // 使用预设策略
 * {@code @BrowserCache(strategy = CacheStrategy.PRODUCT_LIST)}
 * public Result&lt;PageResult&lt;ProductVO&gt;&gt; list(...) { ... }
 *
 * // 自定义 max-age
 * {@code @BrowserCache(strategy = CacheStrategy.PRODUCT_DETAIL, maxAge = 600)}
 * public Result&lt;ProductVO&gt; detail(...) { ... }
 *
 * // 敏感接口禁止缓存
 * {@code @BrowserCache(noStore = true)}
 * public Result&lt;Void&gt; pay(...) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BrowserCache {

    /**
     * 缓存策略，默认使用 {@link CacheStrategy#DEFAULT}。
     * <p>
     * 当同时指定了 {@link #maxAge()} 时，maxAge 会覆盖策略中的默认值。
     */
    CacheStrategy strategy() default CacheStrategy.DEFAULT;

    /**
     * 自定义 max-age 值（秒）。
     * <p>
     * 值大于 0 时覆盖 {@link #strategy()} 中的默认 max-age；
     * 值为 0 时使用策略的默认值。
     */
    int maxAge() default 0;

    /**
     * 是否启用 ETag 生成与校验。
     * <p>
     * 启用后，切面会根据响应体生成 ETag，请求携带 If-None-Match 时比较 ETag，
     * 一致则返回 304 Not Modified（不传输响应体），节省带宽。
     */
    boolean etag() default true;

    /**
     * 是否强制禁止缓存（设置 {@code Cache-Control: no-store}）。
     * <p>
     * 设为 true 时，忽略 {@link #strategy()} 和 {@link #maxAge()}，
     * 强制设置 {@code Cache-Control: no-store}，适用于支付、认证等敏感接口。
     */
    boolean noStore() default false;

    /**
     * 自定义 s-maxage 值（秒），用于 CDN 等共享缓存。
     * <p>
     * 值大于 0 时在 Cache-Control 中追加 s-maxage 指令；
     * 值为 0 时使用 {@link CacheStrategy} 中的默认 s-maxage。
     */
    int sMaxAge() default 0;

    /**
     * Vary 响应头，告诉缓存服务器根据哪些请求头区分缓存版本。
     * <p>
     * 常用值：Accept-Encoding、Accept-Language、Authorization 等。
     * 多个值会合并为逗号分隔的字符串。
     * <p>
     * 默认为空数组，不设置 Vary 头。
     */
    String[] vary() default {};

    /**
     * 提供资源真实最后修改时间的 Spring Bean 类型。
     * <p>
     * 未配置时不生成 Last-Modified，也不处理 If-Modified-Since。
     */
    Class<? extends LastModifiedProvider> lastModifiedProvider()
            default NoLastModifiedProvider.class;
}
