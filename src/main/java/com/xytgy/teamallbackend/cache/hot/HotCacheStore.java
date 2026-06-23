package com.xytgy.teamallbackend.cache.hot;

import com.github.benmanes.caffeine.cache.Cache;
import com.xytgy.teamallbackend.cache.metrics.CacheMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 统一处理热点缓存 L1/L2 的读写语义。
 */
@Slf4j
@RequiredArgsConstructor
class HotCacheStore {
    private final StringRedisTemplate redisTemplate;
    private final HotCacheCodec codec;
    private final CacheMetrics metrics;
    private final Cache<String, String> localCache;

    <T> HotCacheReadResult<T> read(String key, Class<T> type) {
        String json = localCache.getIfPresent(key);
        if (json != null) {
            metrics.recordL1Hit();
            HotCacheReadResult<T> result = decode(json, key, type);
            if (result != null) {
                return result;
            }
        } else {
            metrics.recordL1Miss();
        }

        json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            metrics.recordL2Miss();
            return HotCacheReadResult.miss();
        }
        metrics.recordL2Hit();
        localCache.put(key, json);
        HotCacheReadResult<T> result = decode(json, key, type);
        return result == null ? HotCacheReadResult.miss() : result;
    }

    <T> HotCacheReadResult<T> readUntyped(String key) {
        String json = localCache.getIfPresent(key);
        if (json == null) {
            json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                localCache.put(key, json);
            }
        }
        if (json == null) {
            return HotCacheReadResult.miss();
        }
        try {
            HotCacheRecord<T> record = codec.decode(json);
            return toReadResult(record);
        } catch (Exception e) {
            log.warn("热点缓存反序列化失败, key={}", key, e);
            metrics.recordHotDecodeFailure();
            localCache.invalidate(key);
            return HotCacheReadResult.miss();
        }
    }

    <T> void writeFresh(String key, T value, HotCacheOptions options) {
        try {
            String json = codec.encode(value, options);
            localCache.put(key, json);
            redisTemplate.opsForValue().set(
                    key, json, options.getPhysicalTtl().toMinutes(), TimeUnit.MINUTES);
        } catch (Exception e) {
            metrics.recordHotWriteFailure();
            log.error("热点缓存写入失败, key={}", key, e);
        }
    }

    void invalidateLocal(String key) {
        localCache.invalidate(key);
    }

    void invalidateLocalByPattern(Pattern pattern) {
        localCache.asMap().keySet().removeIf(key -> pattern.matcher(key).matches());
    }

    private <T> HotCacheReadResult<T> decode(String json, String key, Class<T> type) {
        try {
            return toReadResult(codec.decode(json, type));
        } catch (Exception e) {
            metrics.recordHotDecodeFailure();
            log.warn("热点缓存反序列化失败, key={}", key, e);
            localCache.invalidate(key);
            return null;
        }
    }

    private <T> HotCacheReadResult<T> toReadResult(HotCacheRecord<T> record) {
        long now = System.currentTimeMillis();
        if (record.isNullValue()) {
            metrics.recordHotCachedNullHit();
        }
        return record.isLogicallyExpired(now)
                ? HotCacheReadResult.stale(record.getData(), record.isNullValue(),
                record.getLogicalExpireAt(), record.getPhysicalCreatedAt())
                : HotCacheReadResult.fresh(record.getData(), record.isNullValue(),
                record.getLogicalExpireAt(), record.getPhysicalCreatedAt());
    }
}
