package com.xytgy.teamallbackend.cache.facade;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.xytgy.teamallbackend.cache.bloom.BloomFilterManager;
import com.xytgy.teamallbackend.cache.hot.HotCacheRecord;
import com.xytgy.teamallbackend.cache.hot.HotCacheService;
import com.xytgy.teamallbackend.cache.invalidation.CacheInvalidationEventPublisher;
import com.xytgy.teamallbackend.cache.key.VersionedCacheKeyService;
import com.xytgy.teamallbackend.cache.metrics.CacheMetrics;
import com.xytgy.teamallbackend.cache.standard.MultiLevelCacheService;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.LongPredicate;
import java.util.function.Supplier;

/**
 * 缓存兼容门面。
 * <p>
 * 普通两级缓存、热点缓存、缓存失效和指标分别由独立组件负责，
 * 业务层继续通过本类使用原有 API。
 *
 * <p>门面本身不保存缓存数据，也不实现复杂算法。它的作用是稳定业务层接口：
 * 内部缓存实现可以继续拆分或替换，而 Controller、Service 等调用方无需一起修改。
 */
@Component
@RequiredArgsConstructor
public class RedisUtils {

    private final MultiLevelCacheService multiLevelCache;
    private final HotCacheService hotCache;
    private final BloomFilterManager bloomFilterManager;
    private final CacheMetrics metrics;
    private final CacheInvalidationEventPublisher invalidationPublisher;
    private final VersionedCacheKeyService versionedCacheKeyService;

    public <T> T get(String key, Class<T> type) {
        return multiLevelCache.get(key, type);
    }

    public <T> T get(String key, TypeReference<T> typeRef) {
        return multiLevelCache.get(key, typeRef);
    }

    public <T> T getOrLoad(String key, Class<T> type, long ttlMinutes,
                           Supplier<T> loader) {
        return multiLevelCache.getOrLoad(key, type, ttlMinutes, loader);
    }

    public <T> T getOrLoad(String key, TypeReference<T> typeRef, long ttlMinutes,
                           Supplier<T> loader) {
        return multiLevelCache.getOrLoad(key, typeRef, ttlMinutes, loader);
    }

    public <T> T getOrLoadWithBloomFilter(String key, Class<T> type, long ttlMinutes,
                                          Long productId, Supplier<T> loader) {
        // 商品请求先经过布隆过滤器，确定不存在的 ID 不再访问缓存和数据库。
        return getOrLoadWithBloomFilterInternal(
                key, type, ttlMinutes, productId,
                bloomFilterManager::mightContainProduct,
                bloomFilterManager::addProductId,
                loader);
    }

    public <T> T getOrLoadWithBloomFilterForUser(
            String key, Class<T> type, long ttlMinutes,
            Long userId, Supplier<T> loader) {
        return getOrLoadWithBloomFilterInternal(
                key, type, ttlMinutes, userId,
                bloomFilterManager::mightContainUser,
                bloomFilterManager::addUserId,
                loader);
    }

    public <T> void preloadHotData(List<String> keys, Class<T> type, long expireAfter,
                                   Function<String, T> loader) {
        hotCache.preload(keys, type, expireAfter, loader);
    }

    public <T> T getOrLoadHot(String hotKey, Class<T> type, long expireAfter,
                              Supplier<T> loader) {
        return hotCache.getOrLoad(hotKey, type, expireAfter, loader);
    }

    public <T> T getOrLoadHot(String hotKey, long expireAfter, Supplier<T> loader) {
        return hotCache.getOrLoad(hotKey, expireAfter, loader);
    }

    public void set(String key, Object value, long ttlMinutes) {
        multiLevelCache.set(key, value, ttlMinutes);
    }

    @Nullable
    public String getRaw(String key) {
        return multiLevelCache.getRaw(key);
    }

    public long increment(String key) {
        return multiLevelCache.increment(key);
    }

    public void delete(String key) {
        invalidationPublisher.deleteKey(key);
    }

    public void deleteByPattern(String pattern) {
        invalidationPublisher.deletePattern(pattern);
    }

    public String versionedKey(String dataPrefix, String versionKey, String suffix) {
        return versionedCacheKeyService.build(dataPrefix, versionKey, suffix);
    }

    public void invalidateVersion(String versionKey) {
        invalidationPublisher.incrementVersion(versionKey);
    }

    public long getJitteredTtl(long baseMinutes) {
        return multiLevelCache.getJitteredTtl(baseMinutes);
    }

    public long getJitteredTtl(long baseMinutes, double jitterRatio) {
        return multiLevelCache.getJitteredTtl(baseMinutes, jitterRatio);
    }

    public CacheStats getCacheStats() {
        return multiLevelCache.getCacheStats();
    }

    private <T> T getOrLoadWithBloomFilterInternal(
            String key, Class<T> type, long ttlMinutes, Long id,
            LongPredicate mightContain, LongConsumer addId, Supplier<T> loader) {
        if (id == null || !mightContain.test(id)) {
            metrics.recordBloomBlocked();
            return null;
        }

        /*
         * 布隆过滤器只能判断“一定不存在”或“可能存在”。
         * 放行后仍要走正常两级缓存；数据库查到真实数据后再写回过滤器。
         */
        return multiLevelCache.getOrLoad(key, type, ttlMinutes, () -> {
            T result = loader.get();
            if (result != null) {
                addId.accept(id);
            }
            return result;
        });
    }

    /**
     * 旧代码可继续引用该类型；热点 JSON 现在优先使用显式 null/stale 记录。
     */
    @NoArgsConstructor
    public static class HotCacheWrapper<T> extends HotCacheRecord<T> {
        public HotCacheWrapper(T data, long expireAt) {
            super(data, data == null, expireAt, 0L);
        }
    }
}
