package com.xytgy.teamallbackend.config;

import com.xytgy.teamallbackend.utils.RequestUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 可信代理配置。
 * <p>
 * 从 application.yaml 的 {@code trusted.proxies} 读取可信代理 IP 列表，
 * 在应用启动时注入 {@link RequestUtils}，用于安全地解析客户端真实 IP。
 * <p>
 * <b>生产环境部署指南：</b>
 * <ul>
 *   <li>单层 Nginx 代理：设置为 Nginx 服务器的内网 IP</li>
 *   <li>多层代理（如 CDN -> Nginx -> 应用）：需包含所有中间代理的 IP</li>
 *   <li>容器化部署：包含宿主机网桥 IP 或 Docker 网关 IP</li>
 *   <li>通过环境变量 {@code TRUSTED_PROXIES} 覆盖，如 "10.0.0.1,10.0.0.2"</li>
 * </ul>
 *
 * @see RequestUtils#getClientIp(jakarta.servlet.http.HttpServletRequest)
 */
@Slf4j
@Configuration
public class TrustedProxyConfig {

    @Value("${trusted.proxies:127.0.0.1,0:0:0:0:0:0:0:1}")
    private String trustedProxiesRaw;

    @PostConstruct
    public void init() {
        Set<String> proxies = Arrays.stream(trustedProxiesRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());

        RequestUtils.setTrustedProxies(proxies);
        log.info("可信代理 IP 列表已加载: {}", proxies);
    }
}
