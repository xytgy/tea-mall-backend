package com.xytgy.teamallbackend.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "cache")
@Data
public class CacheProperties {
    private Local local = new Local();
    private Bloom bloom = new Bloom();

    @Data
    public static class Local {
        private long maxSize = 512;
        private long expireSeconds = 60;
    }

    @Data
    public static class Bloom {
        private long productExpectedInsertions = 1_000_000;
        private long userExpectedInsertions = 1_000_000;
        private double falsePositiveProbability = 0.01D;
        private int rebuildPageSize = 5_000;
        private Duration rebuildFixedDelay = Duration.ofMinutes(30);
    }
}
