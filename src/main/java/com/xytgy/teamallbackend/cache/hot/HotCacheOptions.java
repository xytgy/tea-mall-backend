package com.xytgy.teamallbackend.cache.hot;

import lombok.Builder;
import lombok.Getter;

import java.time.Duration;

/**
 * 热点缓存策略参数。
 */
@Getter
@Builder
public class HotCacheOptions {
    private final Duration logicalTtl;
    private final Duration physicalTtl;
    private final Duration nullTtl;
    private final Duration maxStaleTtl;
    private final Duration lockTtl;
    private final Duration lockRenewInterval;
    private final Duration retryWait;

    public static HotCacheOptions defaults(long logicalMinutes) {
        Duration logical = Duration.ofMinutes(logicalMinutes);
        return HotCacheOptions.builder()
                .logicalTtl(logical)
                .physicalTtl(logical.multipliedBy(3))
                .nullTtl(Duration.ofMinutes(2))
                .maxStaleTtl(logical.multipliedBy(2))
                .lockTtl(Duration.ofSeconds(10))
                .lockRenewInterval(Duration.ofSeconds(3))
                .retryWait(Duration.ofMillis(50))
                .build();
    }
}
