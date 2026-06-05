package com.xytgy.teamallbackend.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.xytgy.teamallbackend.cache.BloomFilterManager;
import com.xytgy.teamallbackend.properties.CacheProperties;
import com.xytgy.teamallbackend.utils.RedisUtils.HotCacheWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisUtilsTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private BloomFilterManager bloomFilterManager;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RedisUtils redisUtils;
    private Cache<String, String> localCache;
    private String instanceId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        CacheProperties props = buildProperties(512, 60);
        redisUtils = new RedisUtils(stringRedisTemplate, objectMapper, bloomFilterManager, props, null);
        redisUtils.init();

        Field field = RedisUtils.class.getDeclaredField("localCache");
        field.setAccessible(true);
        localCache = (Cache<String, String>) field.get(redisUtils);

        Field idField = RedisUtils.class.getDeclaredField("instanceId");
        idField.setAccessible(true);
        instanceId = (String) idField.get(redisUtils);
    }

    private CacheProperties buildProperties(long maxSize, long expireSeconds) {
        CacheProperties props = new CacheProperties();
        CacheProperties.Local local = new CacheProperties.Local();
        local.setMaxSize(maxSize);
        local.setExpireSeconds(expireSeconds);
        props.setLocal(local);
        return props;
    }

    static class TestDto {
        private String name;

        public TestDto() {}

        public TestDto(String name) { this.name = name; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            return java.util.Objects.equals(name, ((TestDto) o).name);
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(name); }
    }

    private String serializeHot(TestDto data, long expireAt) throws Exception {
        return objectMapper.writeValueAsString(new HotCacheWrapper<>(data, expireAt));
    }

    @SuppressWarnings("unchecked")
    private void stubScan(String... keys) {
        Cursor<String> cursor = mock(Cursor.class);
        var keyList = new ArrayList<>(List.of(keys));
        var iter = keyList.iterator();
        lenient().when(cursor.hasNext()).thenAnswer(inv -> iter.hasNext());
        lenient().when(cursor.next()).thenAnswer(inv -> iter.next());
        lenient().doReturn(cursor).when(stringRedisTemplate).scan(any(ScanOptions.class));
    }

    // ======================== get — 多级缓存读取 ========================

    @Nested
    @DisplayName("get — 多级缓存读取")
    class GetTests {

        @Test
        @DisplayName("L1 命中：直接从本地缓存返回，不访问 Redis")
        void l1Hit_returnsFromLocalCache() {
            localCache.put("test:key", "\"value\"");

            String result = redisUtils.get("test:key", String.class);

            assertEquals("value", result);
            verify(valueOperations, never()).get(anyString());
        }

        @Test
        @DisplayName("L1 未命中、L2 命中：从 Redis 读取并回填本地缓存")
        void l1MissL2Hit_returnsFromRedis() {
            doReturn("\"value\"").when(valueOperations).get("test:key");

            String result = redisUtils.get("test:key", String.class);

            assertEquals("value", result);
            verify(valueOperations).get("test:key");
            assertNotNull(localCache.getIfPresent("test:key"), "L2 命中后应回填 L1");
        }

        @Test
        @DisplayName("L1 和 L2 都未命中：返回 null")
        void bothMiss_returnsNull() {
            doReturn(null).when(valueOperations).get("missing");

            assertNull(redisUtils.get("missing", String.class));
        }

        @Test
        @DisplayName("Redis 返回 NULL 占位符：空值保护，返回 null")
        void nullPlaceholder_returnsNull() {
            doReturn("NULL").when(valueOperations).get("k");

            assertNull(redisUtils.get("k", String.class));
            assertEquals("NULL", localCache.getIfPresent("k"));
        }

        @Test
        @DisplayName("TypeReference 重载：正确反序列化泛型")
        void typeReference_deserializesCorrectly() {
            doReturn("{\"name\":\"x\"}").when(valueOperations).get("k");

            TestDto result = redisUtils.get("k", new TypeReference<TestDto>() {});

            assertEquals(new TestDto("x"), result);
        }

        @Test
        @DisplayName("反序列化失败：返回 null 并删除脏数据")
        void deserializationFails_returnsNullAndDeletes() {
            doReturn("not-json").when(valueOperations).get("k");

            assertNull(redisUtils.get("k", String.class));
            verify(stringRedisTemplate).delete("k");
        }
    }

    // ======================== getOrLoad — Cache-Aside 加载 ========================

    @Nested
    @DisplayName("getOrLoad — Cache-Aside 加载")
    class GetOrLoadTests {

        @Test
        @DisplayName("缓存未命中：调用 loader 并写入两级缓存")
        void cacheMiss_callsLoaderAndCaches() {
            doReturn(null).when(valueOperations).get("k");

            TestDto result = redisUtils.getOrLoad("k", TestDto.class, 5, () -> new TestDto("loaded"));

            assertEquals(new TestDto("loaded"), result);
            verify(valueOperations).set(eq("k"), contains("loaded"), anyLong(), eq(TimeUnit.MINUTES));
        }

        @Test
        @DisplayName("L2 命中：不调用 loader，直接返回")
        void cacheHit_skipsLoader() {
            doReturn("{\"name\":\"cached\"}").when(valueOperations).get("k");

            TestDto result = redisUtils.getOrLoad("k", TestDto.class, 5, () -> {
                fail("loader 不应被调用");
                return null;
            });

            assertEquals(new TestDto("cached"), result);
        }

        @Test
        @DisplayName("Redis 返回 NULL 占位符：直接返回 null，不调用 loader")
        void nullPlaceholder_skipsLoader() {
            doReturn("NULL").when(valueOperations).get("k");

            assertNull(redisUtils.getOrLoad("k", TestDto.class, 5, () -> {
                fail("loader 不应被调用");
                return null;
            }));
        }

        @Test
        @DisplayName("loader 返回 null：写入 NULL 占位符，后续访问不再调用 loader")
        void loaderReturnsNull_writesNullPlaceholder() {
            doReturn(null).when(valueOperations).get("k");

            assertNull(redisUtils.getOrLoad("k", TestDto.class, 5, () -> null));

            verify(valueOperations).set(eq("k"), eq("NULL"), anyLong(), eq(TimeUnit.MINUTES));

            // 第二次访问：L1 已缓存 "NULL"，不会再调 Redis，stub 需标记 lenient
            lenient().doReturn("NULL").when(valueOperations).get("k");
            assertNull(redisUtils.getOrLoad("k", TestDto.class, 5, () -> {
                fail("第二次访问不应调用 loader");
                return null;
            }));
        }

        @Test
        @DisplayName("loader 抛异常：异常透传，缓存不写入脏数据")
        void loaderThrows_propagatesAndNoDirtyCache() {
            doReturn(null).when(valueOperations).get("k");

            assertThrows(RuntimeException.class, () ->
                    redisUtils.getOrLoad("k", TestDto.class, 5, () -> {
                        throw new RuntimeException("DB 故障");
                    }));

            verify(valueOperations, never()).set(eq("k"), anyString(), anyLong(), any());
        }
    }

    // ======================== 布隆过滤器防穿透 ========================

    @Nested
    @DisplayName("getOrLoadWithBloomFilter — 布隆过滤器防穿透")
    class BloomFilterTests {

        @Test
        @DisplayName("布隆过滤器判定 ID 一定不存在：直接返回 null")
        void idNotInFilter_returnsNull() {
            doReturn(false).when(bloomFilterManager).mightContainProduct(999L);

            assertNull(redisUtils.getOrLoadWithBloomFilter(
                    "product:999", TestDto.class, 5, 999L, () -> {
                        fail("loader 不应被调用");
                        return null;
                    }));
            verify(bloomFilterManager, never()).addProductId(any());
        }

        @Test
        @DisplayName("布隆过滤器放行：正常走缓存加载流程")
        void idInFilter_loadsNormally() {
            doReturn(true).when(bloomFilterManager).mightContainProduct(100L);
            doReturn(null).when(valueOperations).get("product:100");

            TestDto result = redisUtils.getOrLoadWithBloomFilter(
                    "product:100", TestDto.class, 5, 100L, () -> new TestDto("p"));

            assertEquals(new TestDto("p"), result);
            verify(bloomFilterManager).addProductId(100L);
        }

        @Test
        @DisplayName("用户 ID 布隆过滤器拦截")
        void userBloomFilter_blocks() {
            doReturn(false).when(bloomFilterManager).mightContainUser(888L);

            assertNull(redisUtils.getOrLoadWithBloomFilterForUser(
                    "user:888", TestDto.class, 5, 888L, () -> {
                        fail("loader 不应被调用");
                        return null;
                    }));
        }
    }

    // ======================== 热点数据逻辑过期 ========================

    @Nested
    @DisplayName("getOrLoadHot — 热点数据逻辑过期")
    class HotDataTests {

        @Test
        @DisplayName("逻辑未过期：直接返回数据")
        void notExpired_returnsData() throws Exception {
            String hotKey = "hot:product:1";
            long expireAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(30);
            String json = serializeHot(new TestDto("hot"), expireAt);
            doReturn(json).when(valueOperations).get(hotKey);

            TestDto result = redisUtils.getOrLoadHot(hotKey, TestDto.class, 30, () -> {
                fail("loader 不应被调用");
                return null;
            });

            assertEquals(new TestDto("hot"), result);
        }

        @Test
        @DisplayName("逻辑过期 + 获取锁成功：调用 loader 刷新缓存并释放锁")
        void expiredLockSuccess_refreshesDataAndReleasesLock() throws Exception {
            String hotKey = "hot:product:1";
            String lockKey = "hot:lock:" + hotKey;
            long pastExpire = System.currentTimeMillis() - 1000;
            String json = serializeHot(new TestDto("stale"), pastExpire);
            doReturn(json).when(valueOperations).get(hotKey);
            doReturn(true).when(valueOperations).setIfAbsent(lockKey, instanceId, 10L, TimeUnit.SECONDS);

            TestDto result = redisUtils.getOrLoadHot(hotKey, TestDto.class, 30, () -> new TestDto("fresh"));

            assertEquals(new TestDto("fresh"), result);
            verify(valueOperations).setIfAbsent(lockKey, instanceId, 10L, TimeUnit.SECONDS);
            verify(stringRedisTemplate).execute(any(), eq(List.of(lockKey)), eq(instanceId));
        }

        @Test
        @DisplayName("逻辑过期 + 获取锁失败：返回旧数据（Stale-While-Revalidate）")
        void expiredLockFail_returnsStaleData() throws Exception {
            String hotKey = "hot:product:1";
            long pastExpire = System.currentTimeMillis() - 1000;
            TestDto staleData = new TestDto("stale");
            String json = serializeHot(staleData, pastExpire);
            doReturn(json).when(valueOperations).get(hotKey);
            doReturn(false).when(valueOperations).setIfAbsent(
                    "hot:lock:" + hotKey, instanceId, 10L, TimeUnit.SECONDS);

            TestDto result = redisUtils.getOrLoadHot(hotKey, TestDto.class, 30, () -> {
                fail("loader 不应被调用");
                return null;
            });

            assertEquals(staleData, result);
        }

        @Test
        @DisplayName("缓存完全不存在：获取锁后调用 loader")
        void noDataAtAll_callsLoader() {
            String hotKey = "hot:product:1";
            doReturn(null).when(valueOperations).get(hotKey);
            doReturn(true).when(valueOperations).setIfAbsent(
                    "hot:lock:" + hotKey, instanceId, 10L, TimeUnit.SECONDS);

            TestDto result = redisUtils.getOrLoadHot(hotKey, TestDto.class, 30, () -> new TestDto("new"));

            assertEquals(new TestDto("new"), result);
            verify(valueOperations).set(eq(hotKey), contains("new"));
        }
    }

    // ======================== set / delete — 写入与删除 ========================

    @Nested
    @DisplayName("set / delete — 写入与删除")
    class WriteDeleteTests {

        @Test
        @DisplayName("set：同时写入 L1 和 L2")
        void set_writesBoth() {
            redisUtils.set("k", new TestDto("v"), 10);

            verify(valueOperations).set(eq("k"), contains("\"name\":\"v\""), anyLong(), eq(TimeUnit.MINUTES));
            assertNotNull(localCache.getIfPresent("k"));
        }

        @Test
        @DisplayName("delete：同时清除 L1 和 L2")
        void delete_removesFromBoth() {
            localCache.put("k", "v");

            redisUtils.delete("k");

            verify(stringRedisTemplate).delete("k");
            assertNull(localCache.getIfPresent("k"));
        }

        @Test
        @DisplayName("deleteByPattern：按通配符匹配删除，不匹配的 key 保留")
        void deleteByPattern_removesMatchingKeys() {
            localCache.put("product:1", "a");
            localCache.put("product:2", "b");
            localCache.put("user:1", "c");
            stubScan("product:1", "product:2");

            redisUtils.deleteByPattern("product:*");

            verify(stringRedisTemplate).delete(argThat((java.util.Collection<String> arg) -> arg.containsAll(List.of("product:1", "product:2"))));
            assertNull(localCache.getIfPresent("product:1"));
            assertNull(localCache.getIfPresent("product:2"));
            assertNotNull(localCache.getIfPresent("user:1"), "不匹配的 key 不应被删除");
        }

        @Test
        @DisplayName("deleteByPattern：SCAN 无匹配 key 时不调用 delete")
        void deleteByPattern_noMatch_skipsDelete() {
            stubScan();

            redisUtils.deleteByPattern("x:*");

            verify(stringRedisTemplate, never()).delete(anyCollection());
        }
    }

    // ======================== TTL 抖动防雪崩 ========================

    @Nested
    @DisplayName("getJitteredTtl — TTL 抖动防雪崩")
    class TtlJitterTests {

        @RepeatedTest(200)
        @DisplayName("基础 TTL=60，抖动结果在 [48, 72] 范围内（±20%）")
        void baseTtl60_jitterInRange() {
            long ttl = redisUtils.getJitteredTtl(60);
            assertTrue(ttl >= 48 && ttl <= 72, () -> "TTL=" + ttl + " 超出 [48,72]");
        }

        @RepeatedTest(100)
        @DisplayName("小 TTL 不会产生非正数")
        void smallTtl_alwaysPositive() {
            assertTrue(redisUtils.getJitteredTtl(1) > 0);
        }

        @RepeatedTest(200)
        @DisplayName("自定义抖动比例 0.3：结果在 [70, 130] 范围内")
        void customRatio_30percent() {
            long ttl = redisUtils.getJitteredTtl(100, 0.3);
            assertTrue(ttl >= 70 && ttl <= 130, () -> "TTL=" + ttl + " 超出 [70,130]");
        }

        @Test
        @DisplayName("无效抖动比例（0 / 1 / 负数）返回原始 TTL")
        void invalidRatio_returnsBase() {
            assertEquals(60, redisUtils.getJitteredTtl(60, 0));
            assertEquals(60, redisUtils.getJitteredTtl(60, 1));
            assertEquals(60, redisUtils.getJitteredTtl(60, -0.5));
        }
    }

    // ======================== 防御性校验 ========================

    @Nested
    @DisplayName("防御性校验 — init() 参数校验")
    class DefensiveTests {

        @ParameterizedTest(name = "maxSize={0}, expireSeconds={1} → 抛 IllegalArgumentException")
        @CsvSource({"0, 60", "-1, 60", "512, 0", "512, -1"})
        void invalidParams_throwsOnInit(long maxSize, long expireSeconds) {
            CacheProperties props = buildProperties(maxSize, expireSeconds);
            RedisUtils invalid = new RedisUtils(stringRedisTemplate, objectMapper, bloomFilterManager, props, null);

            assertThrows(IllegalArgumentException.class, invalid::init);
        }
    }

    // ======================== 缓存统计 ========================

    @Nested
    @DisplayName("getCacheStats — L1 缓存统计")
    class CacheStatsTests {

        @Test
        @DisplayName("命中和未命中后统计计数正确")
        void hitMissCountedCorrectly() {
            doReturn(null).when(valueOperations).get("miss");
            redisUtils.get("miss", String.class);

            localCache.put("hit", "\"v\"");
            redisUtils.get("hit", String.class);

            CacheStats stats = redisUtils.getCacheStats();
            assertTrue(stats.hitCount() >= 1, "至少一次 L1 命中");
            assertTrue(stats.missCount() >= 1, "至少一次 L1 未命中");
        }
    }

    // ======================== 热点数据预热 ========================

    @Nested
    @DisplayName("preloadHotData — 热点数据预热")
    class PreloadTests {

        @Test
        @DisplayName("批量预热：成功加载并写入 Redis，精确验证每个 key")
        void preload_success() {
            doReturn(null).when(valueOperations).get("hot:1");
            doReturn(null).when(valueOperations).get("hot:2");

            redisUtils.preloadHotData(List.of("hot:1", "hot:2"), TestDto.class, 30, TestDto::new);

            verify(valueOperations).set(eq("hot:1"), argThat(json -> json.contains("\"name\":\"hot:1\"")));
            verify(valueOperations).set(eq("hot:2"), argThat(json -> json.contains("\"name\":\"hot:2\"")));
        }

        @Test
        @DisplayName("缓存未过期时跳过预热")
        void preload_existingNotExpired_skips() throws Exception {
            long futureExpire = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(30);
            String existingJson = serializeHot(new TestDto("c"), futureExpire);
            doReturn(existingJson).when(valueOperations).get("hot:1");

            redisUtils.preloadHotData(List.of("hot:1"), TestDto.class, 30, k -> {
                fail("loader 不应被调用");
                return null;
            });

            verify(valueOperations, never()).set(eq("hot:1"), anyString());
        }
    }
}
