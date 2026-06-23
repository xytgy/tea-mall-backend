package com.xytgy.teamallbackend.cache.key;

import com.xytgy.teamallbackend.cache.invalidation.CacheInvalidationRetryService;
import com.xytgy.teamallbackend.cache.metrics.CacheMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 构建版本化缓存 Key，以 O(1) 的版本递增替代核心业务的 Pattern 扫描删除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VersionedCacheKeyService {

    private static final String INITIAL_VERSION = "0";

    private final StringRedisTemplate redisTemplate;
    private final CacheInvalidationRetryService retryService;
    private final CacheMetrics metrics;

    public String build(String dataPrefix, String versionKey, String suffix) {
        validate(dataPrefix, "数据 Key 前缀");
        validate(versionKey, "版本 Key");
        String version = currentVersion(versionKey);
        String normalizedSuffix = suffix == null ? "" : suffix;
        return dataPrefix + "v" + version + ":" + normalizedSuffix;
    }

    public String currentVersion(String versionKey) {
        validate(versionKey, "版本 Key");
        try {
            String version = redisTemplate.opsForValue().get(versionKey);
            return StringUtils.hasText(version) ? version : INITIAL_VERSION;
        } catch (RuntimeException e) {
            // Redis 故障时后续缓存访问通常也会降级，使用初始版本避免构造 Key 失败。
            log.warn("读取缓存版本失败，临时使用初始版本, versionKey={}", versionKey, e);
            return INITIAL_VERSION;
        }
    }

    public void incrementNow(String versionKey) {
        validate(versionKey, "版本 Key");
        try {
            incrementRedisVersion(versionKey);
        } catch (RuntimeException e) {
            metrics.recordInvalidationFailure();
            log.warn("缓存版本递增失败，将异步重试, versionKey={}", versionKey, e);
            retryService.submit(
                    "increment version " + versionKey,
                    () -> incrementRedisVersion(versionKey));
        }
    }

    private void incrementRedisVersion(String versionKey) {
        redisTemplate.opsForValue().increment(versionKey);
    }

    private static void validate(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(name + "不能为空");
        }
    }
}
