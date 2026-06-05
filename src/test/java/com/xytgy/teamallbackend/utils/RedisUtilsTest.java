package com.xytgy.teamallbackend.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.xytgy.teamallbackend.properties.CacheProperties;
import com.xytgy.teamallbackend.utils.RedisUtils.HotCacheWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisUtilsTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private BloomFilterManager bloomFilterManager;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private CacheProperties cacheProperties;

    private RedisUtils redisUtils;

    @BeforeEach
    void setUp() throws Exception {
        cacheProperties = buildProperties(512, 60);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        redisUtils = new RedisUtils(stringRedisTemplate, objectMapper, bloomFilterManager, cacheProperties);
    }

    private CacheProperties buildProperties(long maxSize, long expireSeconds) {
        CacheProperties props = new CacheProperties();
        CacheProperties.Local local = new CacheProperties.Local();
        local.setMaxSize(maxSize);
        local.setExpireSeconds(expireSeconds);
        props.setLocal(local);
        return props;
    }

    @SuppressWarnings("unchecked")
    private Cache<String, String> getLocalCache() throws Exception {
        Field field = RedisUtils.class.getDeclaredField("localCache");
        field.setAccessible(true);
        return (Cache<String, String>) field.get(redisUtils);
    }

    static class TestDto {
        private String name;

        public TestDto() {}

        public TestDto(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TestDto that = (TestDto) o;
            return java.util.Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(name);
        }
    }

    @Nested
    @DisplayName("get — 多级缓存读取")
    class GetTests {

        @Test
        @DisplayName("L1 命中：直接从本地缓存返回，不访问 Redis")
        void l1Hit_returnsFromLocalCache() throws Exception {
            String key = "test:key";
            String cachedJson = "\"value\"";
            getLocalCache().put(key, cachedJson);

            when(objectMapper.readValue(cachedJson, String.class)).thenReturn("value");

            String result = redisUtils.get(key, String.class);

            assertEquals("value", result);
            // 验证 Redis 未被访问
            verify(valueOperations, never()).get(anyString());
        }

        @Test
        @DisplayName("L1 未命中、L2 命中：从 Redis 读取并回填本地缓存")
        void l1MissL2Hit_returnsFromRedis() throws JsonProcessingException {
            String key = "test:key";
            String redisJson = "\"value\"";

            when(valueOperations.get(key)).thenReturn(redisJson);
            when(objectMapper.readValue(redisJson, String.class)).thenReturn("value");

            String result = redisUtils.get(key, String.class);

            assertEquals("value", result);
            verify(valueOperations).get(key);
        }

        @Test
        @DisplayName("L1 和 L2 都未命中：返回 null")
        void bothMiss_returnsNull() {
            when(valueOperations.get("missing")).thenReturn(null);

            assertNull(redisUtils.get("missing", String.class));
        }

        @Test
        @DisplayName("Redis 返回 NULL 占位符：空值保护，返回 null")
        void nullPlaceholder_returnsNull() {
            when(valueOperations.get("k")).thenReturn("NULL");

            assertNull(redisUtils.get("k", String.class));
        }

        @Test
        @DisplayName("TypeReference 重载：正确反序列化泛型")
        void typeReference_deserializesCorrectly() throws JsonProcessingException {
            String key = "k";
            String json = "{\"name\":\"x\"}";
            TypeReference<TestDto> ref = new TypeReference<>() {};
            TestDto expected = new TestDto("x");

            when(valueOperations.get(key)).thenReturn(json);
            when(objectMapper.readValue(eq(json), any(TypeReference.class))).thenReturn(expected);

            assertEquals(expected, redisUtils.get(key, ref));
        }

        @Test
        @DisplayName("反序列化失败：返回 null 并删除脏数据")
        void deserializationFails_returnsNullAndDeletes() throws JsonProcessingException {
            String key = "k";
            String badJson = "not-json";

            when(valueOperations.get(key)).thenReturn(badJson);
            when(objectMapper.readValue(badJson, String.class))
                    .thenThrow(new JsonProcessingException("bad") {});

            assertNull(redisUtils.get(key, String.class));
            verify(stringRedisTemplate).delete(key);
        }
    }

    @Nested
    @DisplayName("getOrLoad — Cache-Aside 加载")
    class GetOrLoadTests {

        @Test
        @DisplayName("缓存未命中：调用 loader 并写入两级缓存")
        void cacheMiss_callsLoaderAndCaches() throws JsonProcessingException {
            String key = "k";
            TestDto loaded = new TestDto("loaded");
            String json = "{\"name\":\"loaded\"}";

            when(valueOperations.get(key)).thenReturn(null);
            when(objectMapper.writeValueAsString(loaded)).thenReturn(json);

            TestDto result = redisUtils.getOrLoad(key, TestDto.class, 5, () -> loaded);

            assertEquals(loaded, result);
            verify(valueOperations).set(eq(key), eq(json), anyLong(), eq(TimeUnit.MINUTES));
        }

        @Test
        @DisplayName("缓存命中：不调用 loader")
        void cacheHit_skipsLoader() throws JsonProcessingException {
            String key = "k";
            String cachedJson = "{\"name\":\"cached\"}";
            TestDto cached = new TestDto("cached");

            when(valueOperations.get(key)).thenReturn(cachedJson);
            when(objectMapper.readValue(cachedJson, TestDto.class)).thenReturn(cached);

            TestDto result = redisUtils.getOrLoad(key, TestDto.class, 5, () -> {
                fail("loader 不应被调用");
                return null;
            });

            assertEquals(cached, result);
        }

        @Test
        @DisplayName("Redis 返回 NULL 占位符：直接返回 null，不调用 loader")
        void nullPlaceholder_skipsLoader() {
            when(valueOperations.get("k")).thenReturn("NULL");

            assertNull(redisUtils.getOrLoad("k", TestDto.class, 5, () -> {
                fail("loader 不应被调用");
                return null;
            }));
        }
    }

    @Nested
    @DisplayName("getOrLoadWithBloomFilter — 布隆过滤器防穿透")
    class BloomFilterTests {

        @Test
        @DisplayName("布隆过滤器判定 ID 一定不存在：直接返回 null")
        void idNotInFilter_returnsNull() {
            Long productId = 999L;
            when(bloomFilterManager.mightContainProduct(productId)).thenReturn(false);

            TestDto result = redisUtils.getOrLoadWithBloomFilter(
                    "product:999", TestDto.class, 5, productId, () -> {
                        fail("loader 不应被调用");
                        return null;
                    });

            assertNull(result);
            verify(bloomFilterManager, never()).addProductId(any());
        }

        @Test
        @DisplayName("布隆过滤器放行：正常走缓存加载流程")
        void idInFilter_loadsNormally() throws JsonProcessingException {
            Long productId = 100L;
            String key = "product:100";
            TestDto loaded = new TestDto("p");
            String json = "{\"name\":\"p\"}";

            when(bloomFilterManager.mightContainProduct(productId)).thenReturn(true);
            when(valueOperations.get(key)).thenReturn(null);
            when(objectMapper.writeValueAsString(loaded)).thenReturn(json);

            TestDto result = redisUtils.getOrLoadWithBloomFilter(
                    key, TestDto.class, 5, productId, () -> loaded);

            assertEquals(loaded, result);
            verify(bloomFilterManager).addProductId(productId);
        }

        @Test
        @DisplayName("用户 ID 布隆过滤器拦截")
        void userBloomFilter_blocks() {
            Long userId = 888L;
            when(bloomFilterManager.mightContainUser(userId)).thenReturn(false);

            assertNull(redisUtils.getOrLoadWithBloomFilterForUser(
                    "user:888", TestDto.class, 5, userId, () -> {
                        fail("loader 不应被调用");
                        return null;
                    }));
        }
    }

    @Nested
    @DisplayName("getOrLoadHot — 热点数据逻辑过期")
    class HotDataTests {

        @Test
        @DisplayName("逻辑未过期：直接返回数据")
        void notExpired_returnsData() throws Exception {
            String hotKey = "hot:product:1";
            long expireAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(30);
            TestDto data = new TestDto("hot");
            HotCacheWrapper<TestDto> wrapper = new HotCacheWrapper<>(data, expireAt);
            String json = "{\"data\":{},\"expireAt\":" + expireAt + "}";

            when(valueOperations.get(hotKey)).thenReturn(json);
            ObjectMapper realMapper = new ObjectMapper();
            // 需要 mock getTypeFactory 链式调用
            com.fasterxml.jackson.databind.type.TypeFactory typeFactory = realMapper.getTypeFactory();
            JavaType javaType = typeFactory.constructParametricType(HotCacheWrapper.class, TestDto.class);
            when(objectMapper.getTypeFactory()).thenReturn(typeFactory);
            when(objectMapper.readValue(json, javaType)).thenReturn(wrapper);

            TestDto result = redisUtils.getOrLoadHot(hotKey, TestDto.class, 30, () -> {
                fail("loader 不应被调用");
                return null;
            });

            assertEquals(data, result);
        }

        @Test
        @DisplayName("逻辑过期 + 获取锁成功：调用 loader 刷新缓存")
        void expiredLockSuccess_refreshesData() throws Exception {
            String hotKey = "hot:product:1";
            long pastExpire = System.currentTimeMillis() - 1000;
            HotCacheWrapper<TestDto> staleWrapper = new HotCacheWrapper<>(new TestDto("stale"), pastExpire);
            String staleJson = "{\"data\":{},\"expireAt\":" + pastExpire + "}";
            TestDto fresh = new TestDto("fresh");

            when(valueOperations.get(hotKey)).thenReturn(staleJson);

            ObjectMapper realMapper = new ObjectMapper();
            com.fasterxml.jackson.databind.type.TypeFactory typeFactory = realMapper.getTypeFactory();
            JavaType javaType = typeFactory.constructParametricType(HotCacheWrapper.class, TestDto.class);
            when(objectMapper.getTypeFactory()).thenReturn(typeFactory);
            when(objectMapper.readValue(staleJson, javaType)).thenReturn(staleWrapper);

            when(valueOperations.setIfAbsent(
                    eq("hot:lock:" + hotKey), eq("1"), eq(10L), eq(TimeUnit.SECONDS)))
                    .thenReturn(true);
            when(objectMapper.writeValueAsString(any(HotCacheWrapper.class)))
                    .thenReturn("{\"data\":{},\"expireAt\":999}");

            TestDto result = redisUtils.getOrLoadHot(hotKey, TestDto.class, 30, () -> fresh);

            assertEquals(fresh, result);
            verify(valueOperations).setIfAbsent(
                    eq("hot:lock:" + hotKey), eq("1"), eq(10L), eq(TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("逻辑过期 + 获取锁失败：返回旧数据（不阻塞）")
        void expiredLockFail_returnsStaleData() throws Exception {
            String hotKey = "hot:product:1";
            long pastExpire = System.currentTimeMillis() - 1000;
            TestDto staleData = new TestDto("stale");
            HotCacheWrapper<TestDto> staleWrapper = new HotCacheWrapper<>(staleData, pastExpire);
            String staleJson = "{\"data\":{},\"expireAt\":" + pastExpire + "}";

            when(valueOperations.get(hotKey)).thenReturn(staleJson);

            ObjectMapper realMapper = new ObjectMapper();
            com.fasterxml.jackson.databind.type.TypeFactory typeFactory = realMapper.getTypeFactory();
            JavaType javaType = typeFactory.constructParametricType(HotCacheWrapper.class, TestDto.class);
            when(objectMapper.getTypeFactory()).thenReturn(typeFactory);
            when(objectMapper.readValue(staleJson, javaType)).thenReturn(staleWrapper);

            when(valueOperations.setIfAbsent(
                    eq("hot:lock:" + hotKey), eq("1"), eq(10L), eq(TimeUnit.SECONDS)))
                    .thenReturn(false);

            TestDto result = redisUtils.getOrLoadHot(hotKey, TestDto.class, 30, () -> {
                fail("loader 不应被调用");
                return null;
            });

            assertEquals(staleData, result);
        }

        @Test
        @DisplayName("缓存完全不存在：获取锁后调用 loader")
        void noDataAtAll_callsLoader() throws JsonProcessingException {
            String hotKey = "hot:product:1";
            TestDto loaded = new TestDto("new");

            when(valueOperations.get(hotKey)).thenReturn(null);
            when(valueOperations.setIfAbsent(
                    eq("hot:lock:" + hotKey), eq("1"), eq(10L), eq(TimeUnit.SECONDS)))
                    .thenReturn(true);
            when(objectMapper.writeValueAsString(any(HotCacheWrapper.class)))
                    .thenReturn("{\"data\":{},\"expireAt\":999}");

            TestDto result = redisUtils.getOrLoadHot(hotKey, TestDto.class, 30, () -> loaded);

            assertEquals(loaded, result);
            // 验证写入 Redis
            verify(valueOperations).set(eq(hotKey), anyString());
        }
    }

    @Nested
    @DisplayName("set / delete — 写入与删除")
    class WriteDeleteTests {

        @Test
        @DisplayName("set：同时写入 L1（Caffeine）和 L2（Redis）")
        void set_writesBoth() throws JsonProcessingException {
            TestDto value = new TestDto("v");
            String json = "{\"name\":\"v\"}";

            when(objectMapper.writeValueAsString(value)).thenReturn(json);

            redisUtils.set("k", value, 10);

            // 验证 Redis 写入（TTL 会经过抖动，用 anyLong）
            verify(valueOperations).set(eq("k"), eq(json), anyLong(), eq(TimeUnit.MINUTES));
            // 验证本地缓存写入
            assertNotNull(getLocalCache().getIfPresent("k"));
        }

        @Test
        @DisplayName("delete：同时清除 L1 和 L2")
        void delete_removesFromBoth() throws Exception {
            String key = "k";
            getLocalCache().put(key, "v");

            redisUtils.delete(key);

            verify(stringRedisTemplate).delete(key);
            assertNull(getLocalCache().getIfPresent(key));
        }

        @Test
        @DisplayName("deleteByPattern：按通配符匹配删除 Redis 和本地缓存")
        void deleteByPattern_removesMatchingKeys() throws Exception {
            String pattern = "product:*";
            Set<String> redisKeys = Set.of("product:1", "product:2");
            getLocalCache().put("product:1", "a");
            getLocalCache().put("product:2", "b");
            getLocalCache().put("user:1", "c");

            when(stringRedisTemplate.keys(pattern)).thenReturn(redisKeys);

            redisUtils.deleteByPattern(pattern);

            verify(stringRedisTemplate).delete(redisKeys);
            assertNull(getLocalCache().getIfPresent("product:1"));
            assertNull(getLocalCache().getIfPresent("product:2"));
            assertNotNull(getLocalCache().getIfPresent("user:1"), "不匹配的 key 不应被删除");
        }

        @Test
        @DisplayName("deleteByPattern：Redis 无匹配 key 时不调用 delete")
        void deleteByPattern_noMatch_skipsDelete() {
            when(stringRedisTemplate.keys("x:*")).thenReturn(null);

            redisUtils.deleteByPattern("x:*");

            verify(stringRedisTemplate, never()).delete(anyCollection());
        }
    }

    @Nested
    @DisplayName("getJitteredTtl — TTL 抖动防雪崩")
    class TtlJitterTests {

        @Test
        @DisplayName("基础 TTL=60，抖动结果在 [48, 72] 范围内（±20%）")
        void baseTtl60_jitterInRange() {
            long base = 60;
            long min = (long) (base * 0.8);
            long max = (long) (base * 1.2);

            for (int i = 0; i < 200; i++) {
                long ttl = redisUtils.getJitteredTtl(base);
                assertTrue(ttl >= min && ttl <= max,
                        "TTL=" + ttl + " 超出 [" + min + "," + max + "]");
            }
        }

        @Test
        @DisplayName("小 TTL 不会产生非正数")
        void smallTtl_alwaysPositive() {
            for (int i = 0; i < 100; i++) {
                assertTrue(redisUtils.getJitteredTtl(1) > 0);
            }
        }

        @Test
        @DisplayName("自定义抖动比例 0.3：结果在 [70, 130] 范围内")
        void customRatio_30percent() {
            long base = 100;
            for (int i = 0; i < 200; i++) {
                long ttl = redisUtils.getJitteredTtl(base, 0.3);
                assertTrue(ttl >= 70 && ttl <= 130,
                        "TTL=" + ttl + " 超出 [70,130]");
            }
        }

        @Test
        @DisplayName("无效抖动比例（0 / 1 / 负数）返回原始 TTL")
        void invalidRatio_returnsBase() {
            assertEquals(60, redisUtils.getJitteredTtl(60, 0));
            assertEquals(60, redisUtils.getJitteredTtl(60, 1));
            assertEquals(60, redisUtils.getJitteredTtl(60, -0.5));
        }
    }

    @Nested
    @DisplayName("防御性校验")
    class DefensiveTests {

        @Test
        @DisplayName("negative maxSize 触发 Caffeine 非法参数异常")
        void negativeMaxSize_throws() {
            CacheProperties props = buildProperties(-1, 60);
            assertThrows(IllegalArgumentException.class,
                    () -> new RedisUtils(stringRedisTemplate, objectMapper, bloomFilterManager, props));
        }

        @Test
        @DisplayName("negative expireSeconds 触发 Caffeine 非法参数异常")
        void negativeExpireSeconds_throws() {
            CacheProperties props = buildProperties(512, -1);
            assertThrows(IllegalArgumentException.class,
                    () -> new RedisUtils(stringRedisTemplate, objectMapper, bloomFilterManager, props));
        }
    }

    @Nested
    @DisplayName("preloadHotData — 热点数据预热")
    class PreloadTests {

        @Test
        @DisplayName("批量预热：成功加载并写入 Redis")
        void preload_success() throws JsonProcessingException {
            List<String> keys = List.of("hot:1", "hot:2");
            TestDto data = new TestDto("p");

            when(valueOperations.get(anyString())).thenReturn(null);
            when(objectMapper.writeValueAsString(any(HotCacheWrapper.class)))
                    .thenReturn("{\"data\":{},\"expireAt\":999}");

            redisUtils.preloadHotData(keys, TestDto.class, 30, k -> data);

            verify(valueOperations, times(2)).set(anyString(), anyString());
        }

        @Test
        @DisplayName("缓存未过期时跳过预热")
        void preload_existingNotExpired_skips() throws Exception {
            List<String> keys = List.of("hot:1");
            long futureExpire = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(30);
            HotCacheWrapper<TestDto> wrapper = new HotCacheWrapper<>(new TestDto("c"), futureExpire);
            String json = "{\"data\":{},\"expireAt\":" + futureExpire + "}";

            when(valueOperations.get("hot:1")).thenReturn(json);

            ObjectMapper realMapper = new ObjectMapper();
            com.fasterxml.jackson.databind.type.TypeFactory typeFactory = realMapper.getTypeFactory();
            JavaType javaType = typeFactory.constructParametricType(HotCacheWrapper.class, TestDto.class);
            when(objectMapper.getTypeFactory()).thenReturn(typeFactory);
            when(objectMapper.readValue(json, javaType)).thenReturn(wrapper);

            redisUtils.preloadHotData(keys, TestDto.class, 30, k -> {
                fail("loader 不应被调用");
                return null;
            });

            // 不应有写入操作
            verify(valueOperations, never()).set(anyString(), anyString());
        }
    }
}