package com.xytgy.teamallbackend.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "flash-sale")
@Data
public class FlashSaleProperties {
    private int captchaPoolSize = 1000;
    private int captchaExpireSeconds = 60;
    private int tokenExpireSeconds = 15;
    private Consumer consumer = new Consumer();
    private RateLimit rateLimit = new RateLimit();
    private Reconcile reconcile = new Reconcile();

    @Data
    public static class Consumer {
        private int batchSize = 50;
        private int pollIntervalMs = 100;
        private int threadCount = 4;
    }

    @Data
    public static class RateLimit {
        private int normalPerSecond = 1;
        private int captchaPerSecond = 10;
        private int blacklistPerMinute = 100;
        private int blacklistDurationSeconds = 600;
    }

    @Data
    public static class Reconcile {
        private boolean enabled = true;
        private long intervalMs = 60000;
        private long cleanupIntervalMs = 3600000;
    }
}
