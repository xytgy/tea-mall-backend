package com.xytgy.teamallbackend.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * L3 浏览器缓存配置属性。
 * <p>
 * 通过 {@code browser-cache.*} 前缀在 application.yaml 中配置，支持：
 * <ul>
 *   <li>全局开关（enabled）</li>
 *   <li>静态资源缓存策略（max-age 等）</li>
 *   <li>按 API 路径前缀配置不同的 Cache-Control 策略</li>
 *   <li>ETag / Last-Modified 开关</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "browser-cache")
@Data
public class BrowserCacheProperties {

    /** 是否启用浏览器缓存层。生产环境建议开启，开发环境建议关闭。 */
    private boolean enabled = true;

    /** 静态资源的 max-age（秒），默认 1 年。 */
    private long staticMaxAge = 31536000L;

    /** 是否启用 ETag 生成与校验。 */
    private boolean enableEtag = true;

    /** 是否启用 Last-Modified 校验。 */
    private boolean enableLastModified = true;

    /** 默认的 Cache-Control 策略，当路径未匹配到任何规则时使用。 */
    private String defaultCacheControl = "no-cache";

    /**
     * 不应被缓存的路径前缀列表（敏感数据）。
     * 匹配这些前缀的请求将强制设置 {@code Cache-Control: no-store}。
     */
    private String[] noStorePrefixes = {"/api/payment", "/api/auth", "/api/feedback"};

    /**
     * API 路径前缀到 Cache-Control 值的映射规则。
     * <p>
     * key 为 URL 路径前缀，value 为对应的 Cache-Control 头值。
     * 匹配时采用最长前缀匹配策略，确保更精确的规则优先。
     */
    private Map<String, String> rules = new HashMap<>();
}
