package com.xytgy.teamallbackend.utils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 多级缓存工具类：L1 (Caffeine 本地) + L2 (Redis 分布式)。
 * <p>
 * 读取路径：L1 → L2 → DB，写入/失效同时清除两级缓存。
 * L1 TTL 远小于 L2，保证跨实例最终一致性；L1 容量小，内存开销可控。
 * <p>
 * 空值保护（防穿透）、TTL 抖动（防雪崩）、逻辑过期防击穿。
 */
@Slf4j
@Component
public class RedisUtils {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final BloomFilterManager bloomFilterManager;

    /** L1 本地缓存：最大 512 条，写入后 60 秒过期 */
    private final Cache<String, String> localCache;

    private static final String NULL_PLACEHOLDER = "NULL";
    private static final long NULL_TTL_MINUTES = 2;
    private static final double TTL_JITTER_RATIO = 0.2;
    private static final String LOCK_PREFIX = "hot:lock:";

    public RedisUtils(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            BloomFilterManager bloomFilterManager,
            @Value("${cache.local.max-size:512}") long localMaxSize,
            @Value("${cache.local.expire-seconds:60}") long localExpireSeconds) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.bloomFilterManager = bloomFilterManager;
        this.localCache = Caffeine.newBuilder()
                .maximumSize(localMaxSize)
                .expireAfterWrite(localExpireSeconds, TimeUnit.SECONDS)
                .build();
        log.info("L1 本地缓存初始化: maxSize={}, expireSeconds={}", localMaxSize, localExpireSeconds);
    }

    // ======================== 读取 ========================

    public <T> T get(String key, Class<T> type) {
        String json = localCache.getIfPresent(key);
        if (json != null) {
            return deserializeL1(json, key, type);
        }
        json = stringRedisTemplate.opsForValue().get(key);
        if (json == null || NULL_PLACEHOLDER.equals(json)) {
            return null;
        }
        localCache.put(key, json);
        return deserialize(json, key, type);
    }

    public <T> T get(String key, TypeReference<T> typeRef) {
        String json = localCache.getIfPresent(key);
        if (json != null) {
            return deserializeL1(json, key, typeRef);
        }
        json = stringRedisTemplate.opsForValue().get(key);
        if (json == null || NULL_PLACEHOLDER.equals(json)) {
            return null;
        }
        localCache.put(key, json);
        return deserialize(json, key, typeRef);
    }

    // ======================== getOrLoad（Cache-Aside + 两级回填）========================

    public <T> T getOrLoad(String key, Class<T> type, long ttlMinutes, Supplier<T> loader) {
        String json = localCache.getIfPresent(key);
        if (json != null) {
            return deserializeL1(json, key, type);
        }
        json = stringRedisTemplate.opsForValue().get(key);
        if (json != null && NULL_PLACEHOLDER.equals(json)) {
            return null;
        }
        if (json != null) {
            localCache.put(key, json);
            return deserialize(json, key, type);
        }
        T result = loader.get();
        writeBoth(key, result, ttlMinutes);
        return result;
    }

    public <T> T getOrLoad(String key, TypeReference<T> typeRef, long ttlMinutes, Supplier<T> loader) {
        String json = localCache.getIfPresent(key);
        if (json != null) {
            return deserializeL1(json, key, typeRef);
        }
        json = stringRedisTemplate.opsForValue().get(key);
        if (json != null && NULL_PLACEHOLDER.equals(json)) {
            return null;
        }
        if (json != null) {
            localCache.put(key, json);
            return deserialize(json, key, typeRef);
        }
        T result = loader.get();
        writeBoth(key, result, ttlMinutes);
        return result;
    }

    // ======================== 带布隆过滤器的缓存加载（防穿透）========================

    /**
     * 带布隆过滤器检查的缓存加载（防穿透）
     * <p>
     * 适用于商品详情等场景，先检查布隆过滤器，如果ID一定不存在则直接返回null，
     * 避免恶意请求穿透到数据库。
     *
     * @param key           缓存key
     * @param type          返回类型
     * @param ttlMinutes    缓存TTL（分钟）
     * @param productId     商品ID（用于布隆过滤器检查）
     * @param loader        回源加载函数
     */
    public <T> T getOrLoadWithBloomFilter(String key, Class<T> type, long ttlMinutes,
                                          Long productId, Supplier<T> loader) {
        // 1. 先检查布隆过滤器，快速拦截一定不存在的ID
        if (!bloomFilterManager.mightContainProduct(productId)) {
            log.debug("布隆过滤器拦截不存在的商品ID: {}", productId);
            return null;
        }

        // 2. 走正常的缓存加载流程
        return getOrLoad(key, type, ttlMinutes, () -> {
            T result = loader.get();
            // 如果加载成功，将ID添加到布隆过滤器
            if (result != null) {
                bloomFilterManager.addProductId(productId);
            }
            return result;
        });
    }

    /**
     * 带布隆过滤器检查的缓存加载（防穿透）- 用户ID版本
     *
     * @param key           缓存key
     * @param type          返回类型
     * @param ttlMinutes    缓存TTL（分钟）
     * @param userId        用户ID（用于布隆过滤器检查）
     * @param loader        回源加载函数
     */
    public <T> T getOrLoadWithBloomFilterForUser(String key, Class<T> type, long ttlMinutes,
                                                 Long userId, Supplier<T> loader) {
        // 1. 先检查布隆过滤器，快速拦截一定不存在的ID
        if (!bloomFilterManager.mightContainUser(userId)) {
            log.debug("布隆过滤器拦截不存在的用户ID: {}", userId);
            return null;
        }

        // 2. 走正常的缓存加载流程
        return getOrLoad(key, type, ttlMinutes, () -> {
            T result = loader.get();
            // 如果加载成功，将ID添加到布隆过滤器
            if (result != null) {
                bloomFilterManager.addUserId(userId);
            }
            return result;
        });
    }

    // ======================== 热点数据预加载 ========================

    /**
     * 热点数据预热
     * <p>
     * 在系统启动时或定时任务中调用，提前加载热点数据到缓存
     * 防止缓存击穿（大量请求同时访问同一个刚过期的key）
     *
     * @param keys         需要预热的key列表
     * @param type         数据类型
     * @param expireAfter  逻辑过期时长（分钟）
     * @param loader       回源加载函数
     */
    public <T> void preloadHotData(List<String> keys, Class<T> type, long expireAfter, 
                                   java.util.function.Function<String, T> loader) {
        log.info("开始预热热点数据，数量: {}", keys.size());
        int successCount = 0;
        int failCount = 0;
        
        for (String key : keys) {
            try {
                // 检查是否已存在缓存
                String existingJson = stringRedisTemplate.opsForValue().get(key);
                if (existingJson != null) {
                    HotCacheWrapper<T> wrapper = deserializeHot(existingJson, key, type);
                    if (wrapper != null && !wrapper.isLogicallyExpired()) {
                        successCount++;
                        continue; // 缓存未过期，跳过
                    }
                }
                
                // 加载数据
                T data = loader.apply(key);
                if (data != null) {
                    long expireAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(expireAfter);
                    HotCacheWrapper<T> wrapper = new HotCacheWrapper<>(data, expireAt);
                    String json = objectMapper.writeValueAsString(wrapper);
                    localCache.put(key, json);
                    stringRedisTemplate.opsForValue().set(key, json);
                    successCount++;
                }
            } catch (Exception e) {
                failCount++;
                log.warn("预热热点数据失败, key={}", key, e);
            }
        }
        
        log.info("热点数据预热完成，成功: {}, 失败: {}", successCount, failCount);
    }

    /**
     * 热点数据逻辑过期加载：key 永不过期，通过逻辑时间戳判断是否过期。
     * <p>
     * 过期时返回旧数据（不阻塞），异步单线程回源刷新。
     * 适用于商品详情、热门话题等高并发读、可容忍短暂不一致的场景。
     *
     * @param hotKey       缓存 key（与物理过期 key 隔离，使用 hot: 前缀）
     * @param type         返回类型
     * @param expireAfter  逻辑过期时长（分钟）
     * @param loader       回源加载函数
     */
    public <T> T getOrLoadHot(String hotKey, Class<T> type, long expireAfter, Supplier<T> loader) {
        String json = localCache.getIfPresent(hotKey);
        if (json != null) {
            HotCacheWrapper<T> wrapper = deserializeHot(json, hotKey, type);
            if (wrapper != null && !wrapper.isLogicallyExpired()) {
                return wrapper.getData();
            }
            // 逻辑已过期，走异步刷新流程
        } else {
            json = stringRedisTemplate.opsForValue().get(hotKey);
            if (json != null) {
                localCache.put(hotKey, json);
                HotCacheWrapper<T> wrapper = deserializeHot(json, hotKey, type);
                if (wrapper != null && !wrapper.isLogicallyExpired()) {
                    return wrapper.getData();
                }
            }
        }

        // 只有一个线程能拿到锁，执行同步回源
        boolean locked = tryHotLock(hotKey);
        if (locked) {
            try {
                T data = loader.get();
                long expireAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(expireAfter);
                HotCacheWrapper<T> wrapper = new HotCacheWrapper<>(data, expireAt);
                String newJson = objectMapper.writeValueAsString(wrapper);
                localCache.put(hotKey, newJson);
                stringRedisTemplate.opsForValue().set(hotKey, newJson);
                return data;
            } catch (Exception e) {
                log.error("热点缓存回源失败, key={}", hotKey, e);
                throw new RuntimeException("热点缓存回源失败", e);
            } finally {
                unlockHot(hotKey);
            }
        }

        // 未拿到锁：返回旧数据（可能过期），等待其他线程刷新
        if (json != null) {
            HotCacheWrapper<T> wrapper = deserializeHot(json, hotKey, type);
            if (wrapper != null) {
                return wrapper.getData();
            }
        }

        // 首次加载且无锁：降级为同步加载
        T data = loader.get();
        long expireAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(expireAfter);
        HotCacheWrapper<T> wrapper = new HotCacheWrapper<>(data, expireAt);
        try {
            String newJson = objectMapper.writeValueAsString(wrapper);
            localCache.put(hotKey, newJson);
            stringRedisTemplate.opsForValue().set(hotKey, newJson);
        } catch (Exception e) {
            log.error("热点缓存首次写入失败, key={}", hotKey, e);
        }
        return data;
    }

    public <T> T getOrLoadHot(String hotKey, TypeReference<T> typeRef, long expireAfter, Supplier<T> loader) {
        String json = localCache.getIfPresent(hotKey);
        if (json != null) {
            HotCacheWrapper<T> wrapper = deserializeHotRef(json, hotKey, typeRef);
            if (wrapper != null && !wrapper.isLogicallyExpired()) {
                return wrapper.getData();
            }
        } else {
            json = stringRedisTemplate.opsForValue().get(hotKey);
            if (json != null) {
                localCache.put(hotKey, json);
                HotCacheWrapper<T> wrapper = deserializeHotRef(json, hotKey, typeRef);
                if (wrapper != null && !wrapper.isLogicallyExpired()) {
                    return wrapper.getData();
                }
            }
        }

        boolean locked = tryHotLock(hotKey);
        if (locked) {
            try {
                T data = loader.get();
                long expireAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(expireAfter);
                HotCacheWrapper<T> wrapper = new HotCacheWrapper<>(data, expireAt);
                String newJson = objectMapper.writeValueAsString(wrapper);
                localCache.put(hotKey, newJson);
                stringRedisTemplate.opsForValue().set(hotKey, newJson);
                return data;
            } catch (Exception e) {
                log.error("热点缓存回源失败, key={}", hotKey, e);
                throw new RuntimeException("热点缓存回源失败", e);
            } finally {
                unlockHot(hotKey);
            }
        }

        if (json != null) {
            HotCacheWrapper<T> wrapper = deserializeHotRef(json, hotKey, typeRef);
            if (wrapper != null) {
                return wrapper.getData();
            }
        }

        T data = loader.get();
        long expireAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(expireAfter);
        HotCacheWrapper<T> wrapper = new HotCacheWrapper<>(data, expireAt);
        try {
            String newJson = objectMapper.writeValueAsString(wrapper);
            localCache.put(hotKey, newJson);
            stringRedisTemplate.opsForValue().set(hotKey, newJson);
        } catch (Exception e) {
            log.error("热点缓存首次写入失败, key={}", hotKey, e);
        }
        return data;
    }

    // ======================== 写入 ========================

    public void set(String key, Object value, long ttlMinutes) {
        writeBoth(key, value, ttlMinutes);
    }

    // ======================== 失效 ========================

    public void delete(String key) {
        localCache.invalidate(key);
        stringRedisTemplate.delete(key);
    }

    public void deleteByPattern(String pattern) {
        String regex = pattern.replace("*", ".*");
        localCache.asMap().keySet().removeIf(k -> k.matches(regex));

        var keys = stringRedisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    // ======================== 内部方法 ========================

    private void writeBoth(String key, Object value, long ttlMinutes) {
        try {
            String json = (value == null) ? NULL_PLACEHOLDER : objectMapper.writeValueAsString(value);
            long l2Ttl = (value == null) ? NULL_TTL_MINUTES : jitterTtl(ttlMinutes);

            localCache.put(key, json);
            stringRedisTemplate.opsForValue().set(key, json, l2Ttl, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            log.error("缓存序列化失败, key={}", key, e);
        }
    }

    private boolean tryHotLock(String hotKey) {
        Boolean result = stringRedisTemplate.opsForValue()
                .setIfAbsent(LOCK_PREFIX + hotKey, "1", 10, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }

    private void unlockHot(String hotKey) {
        stringRedisTemplate.delete(LOCK_PREFIX + hotKey);
    }

    @SuppressWarnings("unchecked")
    private <T> HotCacheWrapper<T> deserializeHot(String json, String key, Class<T> type) {
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructParametricType(HotCacheWrapper.class, type));
        } catch (Exception e) {
            log.warn("热点缓存反序列化失败, key={}", key, e);
            localCache.invalidate(key);
            return null;
        }
    }

    private <T> HotCacheWrapper<T> deserializeHotRef(String json, String key, TypeReference<T> typeRef) {
        try {
            return objectMapper.readValue(json,
                    new TypeReference<HotCacheWrapper<T>>() {});
        } catch (Exception e) {
            log.warn("热点缓存反序列化失败, key={}", key, e);
            localCache.invalidate(key);
            return null;
        }
    }

    private <T> T deserializeL1(String json, String key, Class<T> type) {
        if (NULL_PLACEHOLDER.equals(json)) {
            return null;
        }
        return deserialize(json, key, type);
    }

    private <T> T deserializeL1(String json, String key, TypeReference<T> typeRef) {
        if (NULL_PLACEHOLDER.equals(json)) {
            return null;
        }
        return deserialize(json, key, typeRef);
    }

    private <T> T deserialize(String json, String key, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.warn("缓存反序列化失败, key={}", key, e);
            localCache.invalidate(key);
            stringRedisTemplate.delete(key);
            return null;
        }
    }

    private <T> T deserialize(String json, String key, TypeReference<T> typeRef) {
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            log.warn("缓存反序列化失败, key={}", key, e);
            localCache.invalidate(key);
            stringRedisTemplate.delete(key);
            return null;
        }
    }

    // ======================== TTL 抖动机制 ========================

    /**
     * 基础TTL抖动方法
     * <p>
     * 在基础TTL上添加随机抖动，防止大量缓存同时过期（缓存雪崩）
     * 抖动范围为基础TTL的20%（向上和向下）
     */
    private long jitterTtl(long baseMinutes) {
        long jitterRange = (long) (baseMinutes * TTL_JITTER_RATIO);
        if (jitterRange <= 0) {
            return baseMinutes;
        }
        return baseMinutes + ThreadLocalRandom.current().nextLong(-jitterRange, jitterRange + 1);
    }

    /**
     * 增强版TTL抖动方法
     * <p>
     * 支持自定义抖动比例，适用于不同业务场景
     *
     * @param baseMinutes   基础TTL（分钟）
     * @param jitterRatio   抖动比例（0.0-1.0），例如0.2表示±20%抖动
     */
    private long jitterTtl(long baseMinutes, double jitterRatio) {
        if (jitterRatio <= 0 || jitterRatio >= 1) {
            return baseMinutes;
        }
        long jitterRange = (long) (baseMinutes * jitterRatio);
        if (jitterRange <= 0) {
            return baseMinutes;
        }
        return baseMinutes + ThreadLocalRandom.current().nextLong(-jitterRange, jitterRange + 1);
    }

    /**
     * 获取带抖动的TTL（公开方法，供外部使用）
     * <p>
     * 适用于需要手动设置TTL的场景
     *
     * @param baseMinutes 基础TTL（分钟）
     */
    public long getJitteredTtl(long baseMinutes) {
        return jitterTtl(baseMinutes);
    }

    /**
     * 获取带自定义抖动比例的TTL（公开方法，供外部使用）
     *
     * @param baseMinutes 基础TTL（分钟）
     * @param jitterRatio 抖动比例（0.0-1.0）
     */
    public long getJitteredTtl(long baseMinutes, double jitterRatio) {
        return jitterTtl(baseMinutes, jitterRatio);
    }

    /**
     * 热点缓存包装器：包含数据和逻辑过期时间戳。
     * Redis 中永不过期（或极长 TTL），通过 expireAt 判断是否逻辑过期。
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HotCacheWrapper<T> {
        @JsonProperty("data")
        private T data;

        @JsonProperty("expireAt")
        private long expireAt;

        public boolean isLogicallyExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }
}
