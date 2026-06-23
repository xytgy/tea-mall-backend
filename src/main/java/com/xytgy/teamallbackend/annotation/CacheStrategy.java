package com.xytgy.teamallbackend.annotation;

import lombok.Getter;

/**
 * 浏览器缓存策略枚举。
 * <p>
 * 每种策略对应一组预设的缓存参数，运行时由 {@link #getCacheControl()} 动态拼接 Cache-Control 字符串。
 * 通过 {@link BrowserCache#maxAge()} 可覆盖默认值。
 * <p>
 * 策略说明：
 * <ul>
 *   <li>{@link #STATIC}：静态资源，长期缓存（1 年），适用于带 hash 的 JS/CSS/图片</li>
 *   <li>{@link #PRODUCT_LIST}：商品列表，短时缓存（1 分钟），数据变化较频繁</li>
 *   <li>{@link #PRODUCT_DETAIL}：商品详情，中时缓存（5 分钟），数据相对稳定</li>
 *   <li>{@link #CATEGORY}：分类/轮播图等，中时缓存（10 分钟），变化频率低</li>
 *   <li>{@link #STORE}：店铺相关，短时缓存（2 分钟）</li>
 *   <li>{@link #USER_PRIVATE}：用户相关数据，私有缓存且每次协商验证</li>
 *   <li>{@link #NO_STORE}：敏感数据（支付/认证），禁止任何形式的缓存</li>
 *   <li>{@link #DEFAULT}：默认策略，不缓存但允许协商缓存</li>
 * </ul>
 */
@Getter
public enum CacheStrategy {

    /** 静态资源：public, max-age=31536000（1 年） */
    STATIC("public", false, false, 31536000, 0),

    /** 商品列表：public, max-age=60（1 分钟）, s-maxage=30（CDN 30 秒） */
    PRODUCT_LIST("public", false, false, 60, 30),

    /** 商品详情：public, max-age=300（5 分钟）, s-maxage=120（CDN 2 分钟） */
    PRODUCT_DETAIL("public", false, false, 300, 120),

    /** 分类/轮播图等低频变化数据：public, max-age=600（10 分钟）, s-maxage=300（CDN 5 分钟） */
    CATEGORY("public", false, false, 600, 300),

    /** 店铺相关：public, max-age=120（2 分钟）, s-maxage=60（CDN 1 分钟） */
    STORE("public", false, false, 120, 60),

    /** 用户相关数据：private, no-cache（私有，每次协商验证） */
    USER_PRIVATE("private", false, true, 0, 0),

    /** 敏感数据（支付/认证等）：no-store（禁止缓存） */
    NO_STORE(null, true, false, 0, 0),

    /** 默认策略：no-cache（允许协商缓存，但每次需验证） */
    DEFAULT(null, false, true, 0, 0);

    /** 可见性：public / private / null */
    private final String visibility;

    /** 是否为 no-store 模式 */
    private final boolean noStore;

    /** 是否为 no-cache 模式（每次需协商验证） */
    private final boolean noCache;

    /** 预设的 max-age 值（秒） */
    private final int defaultMaxAge;

    /** 预设的 s-maxage 值（秒），0 表示不添加 s-maxage */
    private final int defaultSMaxAge;

    CacheStrategy(String visibility, boolean noStore, boolean noCache, int defaultMaxAge, int defaultSMaxAge) {
        this.visibility = visibility;
        this.noStore = noStore;
        this.noCache = noCache;
        this.defaultMaxAge = defaultMaxAge;
        this.defaultSMaxAge = defaultSMaxAge;
    }

    /**
     * 根据结构化字段动态拼接 Cache-Control 头值。
     * <p>
     * 优先级：noStore > noCache > visibility + max-age + s-maxage
     *
     * @return 完整的 Cache-Control 头值字符串
     */
    public String getCacheControl() {
        if (noStore) {
            return "no-store";
        }
        StringBuilder sb = new StringBuilder();
        if (visibility != null) {
            sb.append(visibility);
        }
        if (noCache) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append("no-cache");
        }
        if (defaultMaxAge > 0) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append("max-age=").append(defaultMaxAge);
        }
        if (defaultSMaxAge > 0) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append("s-maxage=").append(defaultSMaxAge);
        }
        return sb.toString();
    }
}
