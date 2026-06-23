package com.xytgy.teamallbackend.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 限流配置属性。
 * <p>
 * 通过 {@code rate-limit.*} 前缀在 application.yaml 中配置：
 * <ul>
 *   <li>全局限流（令牌桶）：enabled / capacity / rate</li>
 *   <li>登录限流：login.*</li>
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "rate-limit")
@Data
public class RateLimitProperties {

    /** 是否启用全局限流。 */
    private boolean enabled = true;

    /**
     * 令牌桶容量。
     * 决定允许的突发请求上限。例如 capacity=100 表示最多允许瞬间 100 个请求。
     */
    private int capacity = 100;

    /**
     * 令牌补充速率（每秒）。
     * 决定长期稳定的 QPS 上限。例如 rate=50 表示每秒最多处理 50 个请求。
     */
    private int rate = 50;

    /** 登录相关限流配置。 */
    private Login login = new Login();

    @Data
    public static class Login {
        /** 同一账号每分钟最大登录尝试次数。 */
        private int accountMaxPerMinute = 10;
        /** 同一账号每小时最大登录尝试次数。 */
        private int accountMaxPerHour = 30;
        /** 账号触发小时级限制后的锁定分钟数。 */
        private int accountLockoutMinutes = 15;
        /** 同一 IP 每分钟最大登录尝试次数。 */
        private int ipMaxPerMinute = 20;
        /** 同一 IP 每小时最大登录尝试次数。 */
        private int ipMaxPerHour = 100;
        /** IP 触发小时级限制后的锁定分钟数。 */
        private int ipLockoutMinutes = 15;
    }
}
