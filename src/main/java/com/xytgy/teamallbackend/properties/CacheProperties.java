package com.xytgy.teamallbackend.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cache")
@Data
public class CacheProperties {
    private Local local = new Local();

    @Data
    public static class Local {
        private long maxSize = 512;
        private long expireSeconds = 60;
    }
}
