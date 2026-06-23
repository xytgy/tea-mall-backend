package com.xytgy.teamallbackend.cache.hot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.xytgy.teamallbackend.cache.metrics.CacheMetrics;
import com.xytgy.teamallbackend.properties.CacheProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 热点数据缓存服务门面。
 *
 * <p>内部实现使用显式缓存记录、统一写路径以及独立的锁/存储协调组件。
 * 对业务层继续暴露原有 `getOrLoadHot` 风格 API，避免调用方感知内部重构。
 */
@Slf4j
@Component
public class HotCacheService {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheProperties cacheProperties;
    private final CacheMetrics metrics;

    /** 热点数据专用 L1，不与普通两级缓存共用。 */
    private Cache<String, String> localCache;
    private HotCacheStore store;
    private HotCacheCoordinator coordinator;

    public HotCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                           CacheProperties cacheProperties, CacheMetrics metrics) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheProperties = cacheProperties;
        this.metrics = metrics;
    }

    @PostConstruct
    public void init() {
        CacheProperties.Local local = cacheProperties.getLocal();
        if (local.getMaxSize() <= 0 || local.getExpireSeconds() <= 0) {
            throw new IllegalArgumentException("cache.local configuration must be greater than 0");
        }
        localCache = Caffeine.newBuilder()
                .maximumSize(local.getMaxSize())
                .expireAfterWrite(local.getExpireSeconds(), TimeUnit.SECONDS)
                .build();

        HotCacheCodec codec = new HotCacheCodec(objectMapper);
        store = new HotCacheStore(redisTemplate, codec, metrics, localCache);
        HotCacheLockService lockService = new HotCacheLockService(redisTemplate, metrics);
        coordinator = new HotCacheCoordinator(store, lockService, metrics);
        log.info("热点 L1 缓存初始化: maxSize={}, expireSeconds={}",
                local.getMaxSize(), local.getExpireSeconds());
    }

    public <T> void preload(List<String> keys, Class<T> type, long expireAfter,
                            Function<String, T> loader) {
        log.info("开始预热热点数据，数量: {}", keys.size());
        HotCacheOptions options = HotCacheOptions.defaults(expireAfter);
        int successCount = 0;
        int failCount = 0;

        for (String key : keys) {
            try {
                HotCacheReadResult<T> existing = store.read(key, type);
                if (existing.isHit() && existing.isFresh()) {
                    successCount++;
                    continue;
                }
                store.writeFresh(key, loader.apply(key), options);
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.warn("预热热点数据失败, key={}", key, e);
            }
        }
        log.info("热点数据预热完成，成功: {}, 失败: {}", successCount, failCount);
    }

    public <T> T getOrLoad(String hotKey, Class<T> type, long expireAfter, Supplier<T> loader) {
        return coordinator.getOrLoad(hotKey, type, HotCacheOptions.defaults(expireAfter), loader);
    }

    public <T> T getOrLoad(String hotKey, long expireAfter, Supplier<T> loader) {
        return coordinator.getOrLoad(hotKey, HotCacheOptions.defaults(expireAfter), loader);
    }

    public void invalidateLocal(String key) {
        store.invalidateLocal(key);
    }

    public void invalidateLocalByPattern(Pattern pattern) {
        store.invalidateLocalByPattern(pattern);
    }
}
