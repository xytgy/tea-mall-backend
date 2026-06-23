package com.xytgy.teamallbackend.cache.invalidation;

import com.xytgy.teamallbackend.cache.key.RedisGlobPattern;
import com.xytgy.teamallbackend.cache.local.LocalCacheInvalidator;
import com.xytgy.teamallbackend.cache.metrics.CacheMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 统一执行 Redis 与当前实例 L1 的缓存失效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheKeyCleaner {

    private static final int DELETE_BATCH_SIZE = 100;

    private final StringRedisTemplate redisTemplate;
    private final LocalCacheInvalidator localInvalidator;
    private final CacheInvalidationSyncService syncService;
    private final CacheInvalidationRetryService retryService;
    private final CacheMetrics metrics;

    public void delete(String key) {
        validateKey(key);
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException e) {
            metrics.recordInvalidationFailure();
            log.warn("Redis 缓存删除失败，将异步重试, key={}", key, e);
            retryService.submit("delete key " + key, () -> redisTemplate.delete(key));
        } finally {
            localInvalidator.invalidateKey(key);
        }
        syncService.publishKey(key);
    }

    public void deleteByPattern(String pattern) {
        RedisGlobPattern.validate(pattern);
        long startNanos = System.nanoTime();
        try {
            DeleteResult result = deleteRedisByPattern(pattern);
            log.info("缓存 Pattern 删除完成, pattern={}, scanned={}, deleted={}, costMs={}",
                    pattern, result.scanned(), result.deleted(), elapsedMillis(startNanos));
        } catch (RuntimeException e) {
            metrics.recordInvalidationFailure();
            log.warn("Redis Pattern 删除失败，将异步重试, pattern={}", pattern, e);
            retryService.submit(
                    "delete pattern " + pattern,
                    () -> deleteRedisByPattern(pattern));
        } finally {
            localInvalidator.invalidatePattern(pattern);
        }
        syncService.publishPattern(pattern);
    }

    private DeleteResult deleteRedisByPattern(String pattern) {
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(DELETE_BATCH_SIZE)
                .build();
        long scanned = 0L;
        long deleted = 0L;
        try (var cursor = redisTemplate.scan(options)) {
            Set<String> keys = new LinkedHashSet<>(DELETE_BATCH_SIZE);
            while (cursor.hasNext()) {
                scanned++;
                keys.add(cursor.next());
                if (keys.size() == DELETE_BATCH_SIZE) {
                    deleted += deleteBatch(keys);
                }
            }
            deleted += deleteBatch(keys);
        }
        return new DeleteResult(scanned, deleted);
    }

    private long deleteBatch(Set<String> keys) {
        if (keys.isEmpty()) {
            return 0L;
        }
        Long count = redisTemplate.delete(keys);
        long deleted = count == null ? 0L : count;
        keys.clear();
        return deleted;
    }

    private static void validateKey(String key) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("缓存 Key 不能为空");
        }
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private record DeleteResult(long scanned, long deleted) {
    }
}
