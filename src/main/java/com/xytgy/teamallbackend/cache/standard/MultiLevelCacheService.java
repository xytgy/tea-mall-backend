package com.xytgy.teamallbackend.cache.standard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.xytgy.teamallbackend.cache.hot.HotCacheService;
import com.xytgy.teamallbackend.cache.metrics.CacheMetrics;
import com.xytgy.teamallbackend.lock.DistributedLock;
import com.xytgy.teamallbackend.properties.CacheProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 普通业务数据的两级缓存服务。
 *
 * <p>完整读取顺序为：Caffeine（L1）→ Redis（L2）→ 数据源（通常是数据库）。
 * L1 用来减少网络请求，L2 用来让多个应用实例共享缓存。只有
 * {@link #getOrLoad(String, Class, long, Supplier)} 一类方法会在两级缓存都未命中时回源。
 *
 * <p>本类只处理普通缓存：缓存回源使用分布式锁保护，但不使用热点缓存的逻辑过期策略。
 * 热点缓存场景由 {@link HotCacheService} 负责。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class
MultiLevelCacheService {

    /** 缓存空结果，防止不存在的数据被反复查询数据库。 */
    static final String NULL_PLACEHOLDER = "NULL";

    /**
     * Caffeine 的加载函数不能返回 null，因此用内部哨兵表示 Redis 也未命中。
     * 该值只存在于 L1，不会写入 Redis。
     */
    private static final String CACHE_MISS_SENTINEL = "__CACHE_MISS__";

    /** 空结果只缓存较短时间，避免真实数据创建后长时间仍读取到空值。 */
    private static final long NULL_TTL_MINUTES = 2;

    /** 正常缓存默认在基础 TTL 上增加正负 20% 的随机抖动，降低同时过期风险。 */
    private static final double TTL_JITTER_RATIO = 0.2;

    /** 普通缓存重建锁的业务前缀，底层 DistributedLock 还会统一添加 lock: 前缀。 */
    private static final String LOAD_LOCK_PREFIX = "cache:load:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheProperties cacheProperties;
    private final CacheMetrics metrics;
    private final DistributedLock distributedLock;

    /** L1 统一保存 JSON 字符串，使 L1、L2 使用相同的序列化格式。 */
    private Cache<String, String> localCache;

    @PostConstruct
    public void init() {
        // 使用配置创建 Caffeine，recordStats 用于暴露命中率等本地缓存统计。
        CacheProperties.Local local = cacheProperties.getLocal();
        validateLocalProperties(local);
        localCache = Caffeine.newBuilder()
                .maximumSize(local.getMaxSize())
                .expireAfterWrite(local.getExpireSeconds(), TimeUnit.SECONDS)
                .recordStats()
                .build();
        log.info("普通 L1 缓存初始化: maxSize={}, expireSeconds={}",
                local.getMaxSize(), local.getExpireSeconds());
    }

    public <T> T get(String key, Class<T> type) {
        String json = getCachedJson(key);
        return json == null ? null : deserialize(json, key, objectMapper.readerFor(type));
    }

    public <T> T get(String key, TypeReference<T> typeRef) {
        String json = getCachedJson(key);
        return json == null ? null : deserialize(json, key, objectMapper.readerFor(typeRef));
    }

    public <T> T getOrLoad(String key, Class<T> type, long ttlMinutes, Supplier<T> loader) {
        return getOrLoadInternal(key, json -> deserialize(json, key, objectMapper.readerFor(type)),
                ttlMinutes, loader);
    }

    public <T> T getOrLoad(String key, TypeReference<T> typeRef, long ttlMinutes, Supplier<T> loader) {
        return getOrLoadInternal(key, json -> deserialize(json, key, objectMapper.readerFor(typeRef)),
                ttlMinutes, loader);
    }

    public void set(String key, Object value, long ttlMinutes) {
        writeBoth(key, value, ttlMinutes);
    }

    @Nullable
    public String getRaw(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public long increment(String key) {
        Long value = redisTemplate.opsForValue().increment(key);
        return value == null ? 1 : value;
    }

    public long getJitteredTtl(long baseMinutes) {
        return getJitteredTtl(baseMinutes, TTL_JITTER_RATIO);
    }

    public long getJitteredTtl(long baseMinutes, double jitterRatio) {
        if (jitterRatio <= 0 || jitterRatio >= 1) {
            return baseMinutes;
        }
        long range = (long) (baseMinutes * jitterRatio);
        if (range <= 0) {
            return baseMinutes;
        }
        return baseMinutes + ThreadLocalRandom.current().nextLong(-range, range + 1);
    }

    public CacheStats getCacheStats() {
        return localCache.stats();
    }

    public void invalidateLocal(String key) {
        localCache.invalidate(key);
    }

    public void invalidateLocalByPattern(Pattern pattern) {
        localCache.asMap().keySet().removeIf(key -> pattern.matcher(key).matches());
    }

    private <T> T getOrLoadInternal(String key, Function<String, T> deserializer,
                                    long ttlMinutes, Supplier<T> loader) {
        // 先走 L1/L2；只有完全未命中时才执行 loader，避免不必要的数据库查询。
        String json = getCachedJson(key);
        if (json != null) {
            return NULL_PLACEHOLDER.equals(json) ? null : deserializer.apply(json);
        }

        return loadWithDistributedLock(key, deserializer, ttlMinutes, loader);
    }

    /**
     * 两级缓存都未命中后，通过分布式锁减少多实例同时回源。
     * 获取锁失败时仍会二次检查缓存，最后降级为直接回源以保证接口可用。
     */
    private <T> T loadWithDistributedLock(String key, Function<String, T> deserializer,
                                          long ttlMinutes, Supplier<T> loader) {
        String lockKey = LOAD_LOCK_PREFIX + key;
        boolean locked = false;
        try {
            locked = distributedLock.tryLock(lockKey);
        } catch (RuntimeException e) {
            log.warn("获取缓存重建锁异常，将降级回源, key={}", key, e);
        }

        if (!locked) {
            return retryOrLoad(key, deserializer, ttlMinutes, loader);
        }

        try {
            return retryOrLoad(key, deserializer, ttlMinutes, loader);
        } finally {
            distributedLock.unlock(lockKey);
        }
    }

    private <T> T retryOrLoad(String key, Function<String, T> deserializer,
                              long ttlMinutes, Supplier<T> loader) {
        String json = retryCachedJson(key);
        if (json != null) {
            return NULL_PLACEHOLDER.equals(json) ? null : deserializer.apply(json);
        }
        return loadAndCache(key, ttlMinutes, loader);
    }

    /**
     * 仅清除 L1 中暂存的未命中哨兵，再重新检查缓存。
     * 等待锁期间，其他线程可能已经完成缓存回填，不能误删其写入的真实值。
     */
    private String retryCachedJson(String key) {
        localCache.asMap().remove(key, CACHE_MISS_SENTINEL);
        return getCachedJson(key);
    }

    private <T> T loadAndCache(String key, long ttlMinutes, Supplier<T> loader) {
        T result = metrics.recordSourceLoad(loader);
        writeBoth(key, result, ttlMinutes);
        return result;
    }

    private String getCachedJson(String key) {
        /*
         * Cache#get(key, mappingFunction) 能保证同一 JVM 内同一个 key 的加载是原子的：
         * 多个线程同时访问时，通常只会有一个线程查询 Redis，其余线程复用结果。
         */
        String json = localCache.get(key, cacheKey -> {
            String redisValue = redisTemplate.opsForValue().get(cacheKey);
            if (redisValue == null) {
                metrics.recordL2Miss();
                return CACHE_MISS_SENTINEL;
            }
            metrics.recordL2Hit();
            return redisValue;
        });

        if (CACHE_MISS_SENTINEL.equals(json)) {
            metrics.recordL1Miss();
            return null;
        }
        metrics.recordL1Hit();
        return json;
    }

    private void writeBoth(String key, Object value, long ttlMinutes) {
        try {
            // null 不直接写成 JSON null，而使用明确占位符进行缓存穿透保护。
            String json = value == null ? NULL_PLACEHOLDER : objectMapper.writeValueAsString(value);
            long ttl = value == null ? NULL_TTL_MINUTES : getJitteredTtl(ttlMinutes);
            localCache.put(key, json);
            redisTemplate.opsForValue().set(key, json, ttl, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            log.error("缓存序列化失败, key={}", key, e);
        }
    }

    private <T> T deserialize(String json, String key, ObjectReader reader) {
        if (NULL_PLACEHOLDER.equals(json)) {
            return null;
        }
        try {
            return reader.readValue(json);
        } catch (JsonProcessingException e) {
            // 脏 JSON 留在缓存会让之后每次请求都失败，因此两级缓存一起清除。
            log.warn("缓存反序列化失败, key={}", key, e);
            localCache.invalidate(key);
            redisTemplate.delete(key);
            return null;
        }
    }

    private void validateLocalProperties(CacheProperties.Local local) {
        if (local.getMaxSize() <= 0) {
            throw new IllegalArgumentException(
                    "cache.local.maxSize must be > 0, got: " + local.getMaxSize());
        }
        if (local.getExpireSeconds() <= 0) {
            throw new IllegalArgumentException(
                    "cache.local.expireSeconds must be > 0, got: " + local.getExpireSeconds());
        }
    }
}
