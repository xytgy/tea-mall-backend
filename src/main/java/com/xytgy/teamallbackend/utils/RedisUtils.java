package com.xytgy.teamallbackend.utils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.xytgy.teamallbackend.properties.CacheProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 多级缓存工具类：L1 (Caffeine 本地) + L2 (Redis 分布式)。
 * <p>
 * <b>读取路径</b>：L1 → L2 → DB，写入/失效同时清除两级缓存。<br>
 * L1 TTL 远小于 L2，保证跨实例最终一致性；L1 容量小，内存开销可控。
 * <p>
 * <b>核心防护机制</b>：
 * <ul>
 *   <li>空值保护（NULL_PLACEHOLDER 防穿透）</li>
 *   <li>TTL 抖动（防雪崩）</li>
 *   <li>热点数据逻辑过期 + 分布式锁（防击穿）</li>
 *   <li>布隆过滤器（防穿透）</li>
 * </ul>
 * <p>
 * <b>可观测性</b>：通过 Caffeine {@code recordStats()} 提供 L1 命中率监控，
 * 通过 Micrometer 注册 L1/L2 命中率、回源耗时、锁竞争等指标。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisUtils {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final BloomFilterManager bloomFilterManager;
    private final CacheProperties cacheProperties;

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    /** L1 本地缓存，在 {@link #init()} 中构建 */
    private Cache<String, String> localCache;

    // ======================== Micrometer 指标（在 init 中初始化）========================
    private Counter l1HitCounter;
    private Counter l1MissCounter;
    private Counter l2HitCounter;
    private Counter l2MissCounter;
    private Counter hotLockContendedCounter;
    private Counter bloomBlockedCounter;
    private Timer cacheSourceLoadTimer;

    private static final String NULL_PLACEHOLDER = "NULL";
    private static final long NULL_TTL_MINUTES = 2;
    private static final double TTL_JITTER_RATIO = 0.2;
    private static final String LOCK_PREFIX = "hot:lock:";
    private static final String METRIC_CACHE_NAME_TAG = "cache.name";
    private static final String METRIC_CACHE_NAME_VALUE = "tea-mall";

    /**
     * 初始化 L1 缓存并注册 Micrometer 指标。
     * <p>
     * 校验 {@link CacheProperties.Local} 的 maxSize 和 expireSeconds 必须大于 0。
     */
    @PostConstruct
    public void init() {
        long maxSize = cacheProperties.getLocal().getMaxSize();
        long expireSeconds = cacheProperties.getLocal().getExpireSeconds();

        if (maxSize <= 0) {
            throw new IllegalArgumentException("cache.local.maxSize must be > 0, got: " + maxSize);
        }
        if (expireSeconds <= 0) {
            throw new IllegalArgumentException("cache.local.expireSeconds must be > 0, got: " + expireSeconds);
        }

        this.localCache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(expireSeconds, TimeUnit.SECONDS)
                .recordStats()
                .build();

        log.info("L1 本地缓存初始化: maxSize={}, expireSeconds={}", maxSize, expireSeconds);

        if (meterRegistry != null) {
            l1HitCounter = Counter.builder("cache.l1.hit")
                    .tag(METRIC_CACHE_NAME_TAG, METRIC_CACHE_NAME_VALUE)
                    .description("L1 本地缓存命中次数")
                    .register(meterRegistry);

            l1MissCounter = Counter.builder("cache.l1.miss")
                    .tag(METRIC_CACHE_NAME_TAG, METRIC_CACHE_NAME_VALUE)
                    .description("L1 本地缓存未命中次数")
                    .register(meterRegistry);

            l2HitCounter = Counter.builder("cache.l2.hit")
                    .tag(METRIC_CACHE_NAME_TAG, METRIC_CACHE_NAME_VALUE)
                    .description("L2 Redis 缓存命中次数")
                    .register(meterRegistry);

            l2MissCounter = Counter.builder("cache.l2.miss")
                    .tag(METRIC_CACHE_NAME_TAG, METRIC_CACHE_NAME_VALUE)
                    .description("L2 Redis 缓存未命中次数")
                    .register(meterRegistry);

            cacheSourceLoadTimer = Timer.builder("cache.source.load")
                    .tag(METRIC_CACHE_NAME_TAG, METRIC_CACHE_NAME_VALUE)
                    .description("数据库回源加载耗时")
                    .register(meterRegistry);

            hotLockContendedCounter = Counter.builder("cache.hot.lock.contended")
                    .tag(METRIC_CACHE_NAME_TAG, METRIC_CACHE_NAME_VALUE)
                    .description("热点数据分布式锁竞争失败次数")
                    .register(meterRegistry);

            bloomBlockedCounter = Counter.builder("cache.bloom.blocked")
                    .tag(METRIC_CACHE_NAME_TAG, METRIC_CACHE_NAME_VALUE)
                    .description("布隆过滤器拦截次数")
                    .register(meterRegistry);

            log.info("Micrometer 缓存指标注册完成");
        }
    }

    // ======================== 读取 ========================

    /**
     * 从两级缓存中读取数据并反序列化为指定类型。
     * <p>
     * 读取路径：L1 → L2，利用 Caffeine 的 {@code get(key, mappingFunction)} 实现原子的 compute-if-absent，
     * 避免非原子的 getIfPresent + put 竞态。
     *
     * @param key  缓存 key
     * @param type 目标类型
     * @return 反序列化后的对象，缓存不存在或为空值时返回 {@code null}
     */
    public <T> T get(String key, Class<T> type) {
        String json = localCache.get(key, k -> {
            String redisValue = stringRedisTemplate.opsForValue().get(k);
            if (redisValue == null) {
                incrementCounter(l2MissCounter);
                return null;
            }
            incrementCounter(l2HitCounter);
            return redisValue;
        });

        if (json != null) {
            incrementCounter(l1HitCounter);
            return deserializeL1(json, key, type);
        }

        incrementCounter(l1MissCounter);
        return null;
    }

    /**
     * 从两级缓存中读取数据并使用 {@link TypeReference} 反序列化（支持泛型）。
     *
     * @param key     缓存 key
     * @param typeRef 泛型类型引用
     * @return 反序列化后的对象，缓存不存在或为空值时返回 {@code null}
     */
    public <T> T get(String key, TypeReference<T> typeRef) {
        String json = localCache.get(key, k -> {
            String redisValue = stringRedisTemplate.opsForValue().get(k);
            if (redisValue == null) {
                incrementCounter(l2MissCounter);
                return null;
            }
            incrementCounter(l2HitCounter);
            return redisValue;
        });

        if (json != null) {
            incrementCounter(l1HitCounter);
            return deserializeL1(json, key, typeRef);
        }

        incrementCounter(l1MissCounter);
        return null;
    }

    // ======================== getOrLoad（Cache-Aside + 两级回填）========================

    /**
     * 两级缓存读取 + DB 回源。
     * <p>
     * 流程：L1 → L2 → DB（loader）。利用 Caffeine {@code get()} 将 L1 检查和 L2 回填合并为原子操作。
     * L2 命中则回填 L1 并返回；L2 也未命中时调用 loader，结果同时写入 L1 和 L2。
     *
     * @param key        缓存 key
     * @param type       目标类型
     * @param ttlMinutes L2 缓存 TTL（分钟），会添加随机抖动
     * @param loader     DB 回源加载函数
     * @return 加载结果
     */
    public <T> T getOrLoad(String key, Class<T> type, long ttlMinutes, Supplier<T> loader) {
        String json = localCache.get(key, k -> {
            String redisValue = stringRedisTemplate.opsForValue().get(k);
            if (redisValue == null) {
                incrementCounter(l2MissCounter);
                return null;
            }
            incrementCounter(l2HitCounter);
            return redisValue;
        });

        if (json != null) {
            incrementCounter(l1HitCounter);
            if (NULL_PLACEHOLDER.equals(json)) {
                return null;
            }
            return deserialize(json, key, type);
        }

        incrementCounter(l1MissCounter);
        T result = timedLoad(loader);
        writeBoth(key, result, ttlMinutes);
        return result;
    }

    /**
     * 两级缓存读取 + DB 回源（支持泛型）。
     *
     * @param key        缓存 key
     * @param typeRef    泛型类型引用
     * @param ttlMinutes L2 缓存 TTL（分钟），会添加随机抖动
     * @param loader     DB 回源加载函数
     * @return 加载结果
     */
    public <T> T getOrLoad(String key, TypeReference<T> typeRef, long ttlMinutes, Supplier<T> loader) {
        String json = localCache.get(key, k -> {
            String redisValue = stringRedisTemplate.opsForValue().get(k);
            if (redisValue == null) {
                incrementCounter(l2MissCounter);
                return null;
            }
            incrementCounter(l2HitCounter);
            return redisValue;
        });

        if (json != null) {
            incrementCounter(l1HitCounter);
            if (NULL_PLACEHOLDER.equals(json)) {
                return null;
            }
            return deserialize(json, key, typeRef);
        }

        incrementCounter(l1MissCounter);
        T result = timedLoad(loader);
        writeBoth(key, result, ttlMinutes);
        return result;
    }

    // ======================== 带布隆过滤器的缓存加载（防穿透）========================

    /**
     * 带布隆过滤器检查的缓存加载（防穿透）。
     * <p>
     * 适用于商品详情等场景，先检查布隆过滤器，如果 ID 一定不存在则直接返回 null，
     * 避免恶意请求穿透到数据库。
     *
     * @param key        缓存 key
     * @param type       返回类型
     * @param ttlMinutes 缓存 TTL（分钟）
     * @param productId  商品 ID（用于布隆过滤器检查）
     * @param loader     回源加载函数
     */
    public <T> T getOrLoadWithBloomFilter(String key, Class<T> type, long ttlMinutes,
                                          Long productId, Supplier<T> loader) {
        if (!bloomFilterManager.mightContainProduct(productId)) {
            incrementCounter(bloomBlockedCounter);
            log.debug("布隆过滤器拦截不存在的商品ID: {}", productId);
            return null;
        }

        return getOrLoad(key, type, ttlMinutes, () -> {
            T result = loader.get();
            if (result != null) {
                bloomFilterManager.addProductId(productId);
            }
            return result;
        });
    }

    /**
     * 带布隆过滤器检查的缓存加载（防穿透）- 用户 ID 版本。
     *
     * @param key        缓存 key
     * @param type       返回类型
     * @param ttlMinutes 缓存 TTL（分钟）
     * @param userId     用户 ID（用于布隆过滤器检查）
     * @param loader     回源加载函数
     */
    public <T> T getOrLoadWithBloomFilterForUser(String key, Class<T> type, long ttlMinutes,
                                                 Long userId, Supplier<T> loader) {
        if (!bloomFilterManager.mightContainUser(userId)) {
            incrementCounter(bloomBlockedCounter);
            log.debug("布隆过滤器拦截不存在的用户ID: {}", userId);
            return null;
        }

        return getOrLoad(key, type, ttlMinutes, () -> {
            T result = loader.get();
            if (result != null) {
                bloomFilterManager.addUserId(userId);
            }
            return result;
        });
    }

    // ======================== 热点数据预加载 ========================

    /**
     * 热点数据预热。
     * <p>
     * 在系统启动时或定时任务中调用，提前加载热点数据到缓存，
     * 防止缓存击穿（大量请求同时访问同一个刚过期的 key）。
     * 预热数据使用 {@link HotCacheWrapper} 包装，支持逻辑过期。
     *
     * @param keys        需要预热的 key 列表
     * @param type        数据类型
     * @param expireAfter 逻辑过期时长（分钟）
     * @param loader      回源加载函数（接收 key，返回数据）
     */
    public <T> void preloadHotData(List<String> keys, Class<T> type, long expireAfter,
                                   Function<String, T> loader) {
        log.info("开始预热热点数据，数量: {}", keys.size());
        int successCount = 0;
        int failCount = 0;

        for (String key : keys) {
            try {
                String existingJson = stringRedisTemplate.opsForValue().get(key);
                if (existingJson != null) {
                    HotCacheWrapper<T> wrapper = deserializeHot(existingJson, key, type);
                    if (wrapper != null && !wrapper.isLogicallyExpired()) {
                        successCount++;
                        continue;
                    }
                }

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
     * <p>
     * 热点数据路径保留 {@code getIfPresent}，因为热点数据有独立的逻辑过期判断，
     * 不应被 Caffeine 的 compute-if-absent 阻塞。
     *
     * @param hotKey      缓存 key（与物理过期 key 隔离，使用 hot: 前缀）
     * @param type        返回类型
     * @param expireAfter 逻辑过期时长（分钟）
     * @param loader      回源加载函数
     */
    public <T> T getOrLoadHot(String hotKey, Class<T> type, long expireAfter, Supplier<T> loader) {
        return getOrLoadHotInternal(hotKey, json -> deserializeHot(json, hotKey, type), expireAfter, loader);
    }

    /**
     * 热点数据逻辑过期加载（支持泛型）。
     *
     * @param hotKey      缓存 key
     * @param expireAfter 逻辑过期时长（分钟）
     * @param loader      回源加载函数
     */
    public <T> T getOrLoadHot(String hotKey, long expireAfter, Supplier<T> loader) {
        return getOrLoadHotInternal(hotKey, json -> deserializeHotRef(json, hotKey), expireAfter, loader);
    }

    /**
     * 热点数据加载的模板方法，消除两个 {@code getOrLoadHot} 的重复代码。
     * <p>
     * 流程：尝试读取未过期缓存 → 获取分布式锁回源 → 锁竞争失败时返回旧数据 → 旧数据也无则直接回源。
     */
    private <T> T getOrLoadHotInternal(String hotKey, Function<String, HotCacheWrapper<T>> wrapperDeserializer,
                                        long expireAfter, Supplier<T> loader) {
        T cached = tryReadCachedHot(hotKey, wrapperDeserializer);
        if (cached != null) {
            return cached;
        }

        boolean locked = tryHotLock(hotKey);
        if (locked) {
            try {
                T data = timedLoad(loader);
                writeHotCache(hotKey, data, expireAfter);
                return data;
            } finally {
                unlockHot(hotKey);
            }
        }

        incrementCounter(hotLockContendedCounter);

        // 锁竞争失败，尝试返回旧数据（Stale-While-Revalidate）
        String staleJson = stringRedisTemplate.opsForValue().get(hotKey);
        if (staleJson != null) {
            HotCacheWrapper<T> wrapper = wrapperDeserializer.apply(staleJson);
            if (wrapper != null) {
                return wrapper.getData();
            }
        }

        // 旧数据也无，直接回源
        T data = timedLoad(loader);
        writeHotCache(hotKey, data, expireAfter);
        return data;
    }

    /**
     * 尝试从 L1/L2 读取热点缓存，仅返回逻辑未过期的数据。
     * <p>
     * 保留 {@code getIfPresent}：热点数据的逻辑过期由 {@link HotCacheWrapper#isLogicallyExpired()} 控制，
     * L1 的物理 TTL 仅作为兜底，不应使用 compute-if-absent 阻塞当前线程。
     */
    private <T> T tryReadCachedHot(String hotKey, Function<String, HotCacheWrapper<T>> wrapperDeserializer) {
        String json = localCache.getIfPresent(hotKey);
        if (json == null) {
            json = stringRedisTemplate.opsForValue().get(hotKey);
            if (json != null) {
                localCache.put(hotKey, json);
            }
        }
        if (json == null) {
            return null;
        }
        HotCacheWrapper<T> wrapper = wrapperDeserializer.apply(json);
        if (wrapper == null || wrapper.isLogicallyExpired()) {
            return null;
        }
        return wrapper.getData();
    }

    private <T> void writeHotCache(String hotKey, T data, long expireAfter) {
        try {
            long expireAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(expireAfter);
            HotCacheWrapper<T> wrapper = new HotCacheWrapper<>(data, expireAt);
            String json = objectMapper.writeValueAsString(wrapper);
            localCache.put(hotKey, json);
            stringRedisTemplate.opsForValue().set(hotKey, json);
        } catch (JsonProcessingException e) {
            log.error("热点缓存写入失败, key={}", hotKey, e);
        }
    }

    // ======================== 写入 ========================

    /**
     * 写入数据到两级缓存。
     *
     * @param key        缓存 key
     * @param value      值（为 null 时写入 NULL_PLACEHOLDER 空值保护）
     * @param ttlMinutes L2 缓存 TTL（分钟），会添加随机抖动
     */
    public void set(String key, Object value, long ttlMinutes) {
        writeBoth(key, value, ttlMinutes);
    }

    // ======================== 失效 ========================

    /**
     * 删除指定 key 的两级缓存。
     *
     * @param key 缓存 key
     */
    public void delete(String key) {
        localCache.invalidate(key);
        stringRedisTemplate.delete(key);
    }

    /**
     * 按模式删除两级缓存。L1 使用正则匹配，L2 使用 Redis KEYS 命令。
     * <p>
     * <b>注意</b>：Redis KEYS 命令在大数据量下有性能风险，生产环境请谨慎使用。
     *
     * @param pattern Redis key 模式（支持 * 通配符）
     */
    public void deleteByPattern(String pattern) {
        String regex = pattern.replace("*", ".*");
        localCache.asMap().keySet().removeIf(k -> k.matches(regex));

        var keys = stringRedisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    // ======================== TTL 抖动机制 ========================

    /**
     * 获取带抖动的 TTL（公开方法，供外部使用）。
     * <p>
     * 在基础 TTL 上添加 ±20% 随机抖动，防止大量缓存同时过期（缓存雪崩）。
     *
     * @param baseMinutes 基础 TTL（分钟）
     * @return 添加抖动后的 TTL（分钟）
     */
    public long getJitteredTtl(long baseMinutes) {
        return jitterTtl(baseMinutes);
    }

    /**
     * 获取带自定义抖动比例的 TTL（公开方法，供外部使用）。
     *
     * @param baseMinutes 基础 TTL（分钟）
     * @param jitterRatio 抖动比例（0.0-1.0），例如 0.2 表示 ±20% 抖动
     * @return 添加抖动后的 TTL（分钟）
     */
    public long getJitteredTtl(long baseMinutes, double jitterRatio) {
        return jitterTtl(baseMinutes, jitterRatio);
    }

    // ======================== 缓存统计 ========================

    /**
     * 获取 L1 本地缓存的统计数据。
     * <p>
     * 返回 Caffeine {@link CacheStats}，包含命中率、未命中率、加载次数、驱逐次数等信息。
     * 仅在 {@link Caffeine#recordStats()} 启用时有效。
     *
     * @return L1 缓存统计数据
     */
    public CacheStats getCacheStats() {
        return localCache.stats();
    }

    // ======================== 内部方法 ========================

    /**
     * 同时写入 L1 和 L2 缓存。
     * <p>
     * L2 TTL 会添加随机抖动防雪崩；null 值写入 {@link #NULL_PLACEHOLDER} 防穿透。
     */
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

    /**
     * 尝试获取热点数据分布式锁，锁持有时间为 10 秒。
     *
     * @return {@code true} 表示获取成功
     */
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

    private <T> HotCacheWrapper<T> deserializeHotRef(String json, String key) {
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

    private long jitterTtl(long baseMinutes) {
        long jitterRange = (long) (baseMinutes * TTL_JITTER_RATIO);
        if (jitterRange <= 0) {
            return baseMinutes;
        }
        return baseMinutes + ThreadLocalRandom.current().nextLong(-jitterRange, jitterRange + 1);
    }

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
     * 带 Timer 计时的 loader 调用，仅在 meterRegistry 可用时记录耗时。
     */
    private <T> T timedLoad(Supplier<T> loader) {
        if (cacheSourceLoadTimer != null) {
            return cacheSourceLoadTimer.record(loader::get);
        }
        return loader.get();
    }

    /**
     * 安全地递增 Counter，仅在 meterRegistry 可用时执行。
     */
    private void incrementCounter(Counter counter) {
        if (counter != null) {
            counter.increment();
        }
    }

    /**
     * 热点缓存包装器：包含数据和逻辑过期时间戳。
     * <p>
     * Redis 中永不过期（或极长 TTL），通过 {@code expireAt} 判断是否逻辑过期。
     * 配合分布式锁实现热点数据的无阻塞刷新。
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
