package com.xytgy.teamallbackend.config;

import com.xytgy.teamallbackend.properties.BrowserCacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * L3 浏览器缓存 WebMvc 配置。
 * <p>
 * 职责：
 * <ul>
 *   <li>为静态资源配置长期缓存头（Cache-Control: public, max-age=31536000）</li>
 *   <li>启用 {@link BrowserCacheProperties} 配置属性绑定</li>
 * </ul>
 * <p>
 * 与现有 {@link WebConfig} 的关系：
 * {@link WebConfig} 负责静态资源的路径映射和 SPA 回退，
 * 此配置专注于缓存策略的设置。两者协同工作，互不冲突。
 * <p>
 * 注意：静态资源的缓存头通过 ResourceHandlerRegistration 的 cachePeriod 设置，
 * API 响应的缓存由 {@link com.xytgy.teamallbackend.filter.CacheHeaderFilter} 和
 * {@link com.xytgy.teamallbackend.aspect.BrowserCacheAspect} 处理。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(BrowserCacheProperties.class)
public class BrowserCacheConfig implements WebMvcConfigurer {

    private final BrowserCacheProperties browserCacheProperties;

    /**
     * 为静态资源配置浏览器缓存策略。
     * <p>
     * Spring MVC 的 {@code cachePeriod} 设置的是秒级的 Cache-Control max-age，
     * 浏览器会据此缓存静态资源，减少重复请求。
     * <p>
     * 注意：此方法不添加资源处理器（由 WebConfig 已处理），
     * 仅在 WebConfig 的资源处理器基础上补充缓存配置说明。
     * 实际的静态资源缓存通过 CacheHeaderFilter 的路径匹配实现，
     * 这里仅做日志输出，便于运维确认配置生效。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (!browserCacheProperties.isEnabled()) {
            log.info("L3 浏览器缓存层已禁用");
            return;
        }

        log.info("L3 浏览器缓存层初始化: staticMaxAge={}, enableEtag={}, enableLastModified={}",
                browserCacheProperties.getStaticMaxAge(),
                browserCacheProperties.isEnableEtag(),
                browserCacheProperties.isEnableLastModified());

        if (!browserCacheProperties.getRules().isEmpty()) {
            log.info("浏览器缓存规则: {}", browserCacheProperties.getRules());
        }
    }
}
