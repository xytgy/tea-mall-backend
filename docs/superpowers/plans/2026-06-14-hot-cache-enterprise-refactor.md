# Hot Cache Enterprise Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor `HotCacheService` into a safer, enterprise-grade hot-cache subsystem with explicit null caching, renewable lock ownership, unified write semantics, and bounded stale fallback.

**Architecture:** Keep `RedisUtils` business-facing APIs stable while decomposing the current monolithic hot-cache logic into focused collaborators. Land the refactor incrementally behind the existing entry points so tests can validate behavior at each stage before internal responsibilities are moved.

**Tech Stack:** Spring Boot, `StringRedisTemplate`, Caffeine, Jackson `ObjectMapper`, JUnit 5, Mockito

---

## File Structure

### Existing files to modify

- `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheService.java`
  Current orchestration class; will shrink into facade or thin compatibility wrapper.
- `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheEntry.java`
  Current Redis payload model; will be replaced or evolved into explicit null/stale-aware record model.
- `src/main/java/com/xytgy/teamallbackend/cache/multilevel/CacheMetrics.java`
  Add hot-cache-specific metrics for stale returns, cached-null hits, lock outcomes, and write/decode failures.
- `src/main/java/com/xytgy/teamallbackend/utils/RedisUtils.java`
  Keep external hot-cache API stable while wiring to the refactored internals.
- `src/test/java/com/xytgy/teamallbackend/utils/RedisUtilsTest.java`
  Extend unit coverage for hot-cache null semantics, preload consistency, stale serving, and lock behavior.

### New files to create

- `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheRecord.java`
  Explicit cache payload model with `data`, `nullValue`, `logicalExpireAt`, and `physicalCreatedAt`.
- `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheOptions.java`
  Policy object for logical TTL, physical TTL, null TTL, max stale TTL, lock TTL, and retry wait.
- `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheCodec.java`
  Jackson boundary for encoding and decoding hot-cache payloads.
- `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheStore.java`
  Unified L1/L2 read-write component.
- `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheReadResult.java`
  Explicit read classification object for miss, fresh, stale, and cached-null outcomes.
- `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheLockService.java`
  Redis lock abstraction returning a closeable handle with per-acquisition token.
- `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheCoordinator.java`
  Main hot-cache workflow orchestration component.

## Task 1: Introduce explicit hot-cache payload and options model

**Files:**
- Create: `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheRecord.java`
- Create: `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheOptions.java`
- Create: `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheReadResult.java`
- Modify: `src/test/java/com/xytgy/teamallbackend/utils/RedisUtilsTest.java`

- [ ] **Step 1: Write the failing tests for explicit null and stale semantics**

```java
@Test
@DisplayName("热点缓存记录可显式表达缓存空值")
void hotCacheRecord_supportsExplicitNullValue() {
    HotCacheRecord<TestDto> record = new HotCacheRecord<>(null, true, 200L, 100L);

    assertTrue(record.isNullValue());
    assertNull(record.getData());
    assertEquals(200L, record.getLogicalExpireAt());
}

@Test
@DisplayName("热点读取结果可区分 fresh 和 stale")
void hotCacheReadResult_distinguishesFreshAndStale() {
    HotCacheReadResult<TestDto> fresh = HotCacheReadResult.fresh(new TestDto("ok"), false, 200L);
    HotCacheReadResult<TestDto> stale = HotCacheReadResult.stale(new TestDto("old"), false, 100L);

    assertTrue(fresh.isHit());
    assertTrue(fresh.isFresh());
    assertTrue(stale.isHit());
    assertFalse(stale.isFresh());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -Dtest=RedisUtilsTest test`
Expected: FAIL with missing `HotCacheRecord` / `HotCacheReadResult` symbols

- [ ] **Step 3: Add the new model classes**

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HotCacheRecord<T> {
    private T data;
    private boolean nullValue;
    private long logicalExpireAt;
    private long physicalCreatedAt;

    @JsonIgnore
    public boolean isLogicallyExpired(long now) {
        return now > logicalExpireAt;
    }
}
```

```java
@Getter
@Builder
public class HotCacheOptions {
    private Duration logicalTtl;
    private Duration physicalTtl;
    private Duration nullTtl;
    private Duration maxStaleTtl;
    private Duration lockTtl;
    private Duration lockRenewInterval;
    private Duration retryWait;

    public static HotCacheOptions defaults(Duration logicalTtl) {
        return HotCacheOptions.builder()
                .logicalTtl(logicalTtl)
                .physicalTtl(logicalTtl.multipliedBy(3))
                .nullTtl(Duration.ofMinutes(2))
                .maxStaleTtl(logicalTtl.multipliedBy(2))
                .lockTtl(Duration.ofSeconds(10))
                .lockRenewInterval(Duration.ofSeconds(3))
                .retryWait(Duration.ofMillis(50))
                .build();
    }
}
```

```java
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class HotCacheReadResult<T> {
    private final boolean hit;
    private final boolean fresh;
    private final boolean nullValue;
    private final T data;
    private final long logicalExpireAt;

    public static <T> HotCacheReadResult<T> miss() {
        return new HotCacheReadResult<>(false, false, false, null, 0L);
    }

    public static <T> HotCacheReadResult<T> fresh(T data, boolean nullValue, long logicalExpireAt) {
        return new HotCacheReadResult<>(true, true, nullValue, data, logicalExpireAt);
    }

    public static <T> HotCacheReadResult<T> stale(T data, boolean nullValue, long logicalExpireAt) {
        return new HotCacheReadResult<>(true, false, nullValue, data, logicalExpireAt);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -Dtest=RedisUtilsTest test`
Expected: PASS for the newly added model tests

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheRecord.java \
  src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheOptions.java \
  src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheReadResult.java \
  src/test/java/com/xytgy/teamallbackend/utils/RedisUtilsTest.java
git commit -m "refactor: add explicit hot cache record model"
```

## Task 2: Extract codec and unified store semantics

**Files:**
- Create: `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheCodec.java`
- Create: `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheStore.java`
- Modify: `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheService.java`
- Modify: `src/test/java/com/xytgy/teamallbackend/utils/RedisUtilsTest.java`

- [ ] **Step 1: Write the failing tests for preload TTL consistency and cached-null read**

```java
@Test
@DisplayName("预热热点数据与正常写入使用一致的 Redis TTL")
void preload_usesSamePhysicalTtlAsWrite() {
    hotCacheService.preload(List.of("hot:k"), TestDto.class, 5, key -> new TestDto("warm"));

    verify(valueOperations).set(eq("hot:k"), contains("logicalExpireAt"), eq(15L), eq(TimeUnit.MINUTES));
}

@Test
@DisplayName("热点缓存命中 cached-null 时不触发 loader")
void hotCache_cachedNullHit_skipsLoader() {
    String json = objectMapper.writeValueAsString(new HotCacheRecord<>(null, true, Long.MAX_VALUE, 1L));
    hotLocalCache.put("hot:k", json);

    TestDto result = redisUtils.getOrLoadHot("hot:k", TestDto.class, 5, () -> {
        fail("loader 不应被调用");
        return new TestDto("x");
    });

    assertNull(result);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -Dtest=RedisUtilsTest#preload_usesSamePhysicalTtlAsWrite,RedisUtilsTest#hotCache_cachedNullHit_skipsLoader test`
Expected: FAIL because preload does not set physical TTL consistently and hot-cache null semantics are not implemented

- [ ] **Step 3: Implement codec and store**

```java
@Component
@RequiredArgsConstructor
public class HotCacheCodec {
    private final ObjectMapper objectMapper;

    public <T> String encode(T value, HotCacheOptions options) throws JsonProcessingException {
        long now = System.currentTimeMillis();
        boolean nullValue = value == null;
        long logicalExpireAt = now + (nullValue ? options.getNullTtl() : options.getLogicalTtl()).toMillis();
        HotCacheRecord<T> record = new HotCacheRecord<>(value, nullValue, logicalExpireAt, now);
        return objectMapper.writeValueAsString(record);
    }

    public <T> HotCacheRecord<T> decode(String json, JavaType type) throws JsonProcessingException {
        JavaType recordType = objectMapper.getTypeFactory()
                .constructParametricType(HotCacheRecord.class, type);
        return objectMapper.readValue(json, recordType);
    }
}
```

```java
@Component
@RequiredArgsConstructor
public class HotCacheStore {
    private final StringRedisTemplate redisTemplate;
    private final HotCacheCodec codec;
    private final CacheMetrics metrics;
    private Cache<String, String> localCache;

    public <T> void writeFresh(String key, T value, HotCacheOptions options) {
        String json = codec.encode(value, options);
        localCache.put(key, json);
        redisTemplate.opsForValue().set(key, json, options.getPhysicalTtl().toMinutes(), TimeUnit.MINUTES);
    }
}
```

- [ ] **Step 4: Rewire `HotCacheService` preload and write paths to use store**

```java
public <T> void preload(List<String> keys, Class<T> type, long expireAfter, Function<String, T> loader) {
    HotCacheOptions options = HotCacheOptions.defaults(Duration.ofMinutes(expireAfter));
    for (String key : keys) {
        T data = loader.apply(key);
        store.writeFresh(key, data, options);
    }
}
```

- [ ] **Step 5: Run tests and commit**

Run: `./mvnw -Dtest=RedisUtilsTest test`
Expected: PASS with preload TTL and cached-null coverage

```bash
git add src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheCodec.java \
  src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheStore.java \
  src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheService.java \
  src/test/java/com/xytgy/teamallbackend/utils/RedisUtilsTest.java
git commit -m "refactor: unify hot cache storage semantics"
```

## Task 3: Extract renewable lock service

**Files:**
- Create: `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheLockService.java`
- Modify: `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheService.java`
- Modify: `src/main/java/com/xytgy/teamallbackend/cache/multilevel/CacheMetrics.java`
- Modify: `src/test/java/com/xytgy/teamallbackend/utils/RedisUtilsTest.java`

- [ ] **Step 1: Write the failing tests for per-acquisition token safety**

```java
@Test
@DisplayName("每次热点锁获取都生成唯一 token")
void hotLock_generatesUniqueTokenPerAcquire() {
    LockHandle first = hotCacheLockService.tryAcquire("hot:k", Duration.ofSeconds(10)).orElseThrow();
    LockHandle second = hotCacheLockService.tryAcquire("hot:k2", Duration.ofSeconds(10)).orElseThrow();

    assertNotEquals(first.token(), second.token());
}

@Test
@DisplayName("释放锁时使用当前 token 做 compare-and-delete")
void hotLock_unlockUsesHandleToken() {
    LockHandle handle = hotCacheLockService.tryAcquire("hot:k", Duration.ofSeconds(10)).orElseThrow();

    handle.close();

    verify(stringRedisTemplate).execute(any(), eq(List.of("hot:lock:hot:k")), eq(handle.token()));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -Dtest=RedisUtilsTest#hotLock_generatesUniqueTokenPerAcquire,RedisUtilsTest#hotLock_unlockUsesHandleToken test`
Expected: FAIL because lock service and lock handle do not exist

- [ ] **Step 3: Implement lock service with closeable handle**

```java
public interface HotCacheLockService {
    Optional<LockHandle> tryAcquire(String key, Duration ttl);

    interface LockHandle extends AutoCloseable {
        String token();
        @Override
        void close();
    }
}
```

```java
String token = UUID.randomUUID().toString();
Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, token, ttl);
return Boolean.TRUE.equals(locked) ? Optional.of(new RedisHotLockHandle(lockKey, token)) : Optional.empty();
```

- [ ] **Step 4: Add renewal and metrics hooks**

```java
ScheduledFuture<?> renewTask = scheduler.scheduleAtFixedRate(
        () -> renew(lockKey, token, ttl),
        renewInterval.toMillis(),
        renewInterval.toMillis(),
        TimeUnit.MILLISECONDS);
```

```java
public void recordHotLockAcquireSuccess() { increment(hotLockAcquireSuccess); }
public void recordHotLockAcquireFailure() { increment(hotLockAcquireFailure); }
public void recordHotLockRenewFailure() { increment(hotLockRenewFailure); }
```

- [ ] **Step 5: Run tests and commit**

Run: `./mvnw -Dtest=RedisUtilsTest test`
Expected: PASS for new lock behavior tests

```bash
git add src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheLockService.java \
  src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheService.java \
  src/main/java/com/xytgy/teamallbackend/cache/multilevel/CacheMetrics.java \
  src/test/java/com/xytgy/teamallbackend/utils/RedisUtilsTest.java
git commit -m "refactor: extract renewable hot cache lock service"
```

## Task 4: Introduce coordinator flow with bounded stale fallback

**Files:**
- Create: `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheCoordinator.java`
- Modify: `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheService.java`
- Modify: `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheStore.java`
- Modify: `src/main/java/com/xytgy/teamallbackend/cache/multilevel/CacheMetrics.java`
- Modify: `src/test/java/com/xytgy/teamallbackend/utils/RedisUtilsTest.java`

- [ ] **Step 1: Write the failing tests for stale return and first-miss retry**

```java
@Test
@DisplayName("逻辑过期但仍在 maxStale 窗口内时返回旧值")
void staleWithinWindow_returnsStaleValue() throws Exception {
    long now = System.currentTimeMillis();
    String staleJson = objectMapper.writeValueAsString(
            new HotCacheRecord<>(new TestDto("old"), false, now - 1_000L, now - 2_000L));
    doReturn(staleJson).when(valueOperations).get("hot:k");
    doReturn(false).when(lockService).tryAcquire(eq("hot:k"), any());

    TestDto result = redisUtils.getOrLoadHot("hot:k", TestDto.class, 5, () -> new TestDto("fresh"));

    assertEquals(new TestDto("old"), result);
}

@Test
@DisplayName("首次 miss 锁失败时先短暂等待并重试缓存")
void firstMiss_lockFail_retriesCacheBeforeFallback() {
    doReturn(null, "{\"data\":{\"name\":\"warm\"},\"nullValue\":false,\"logicalExpireAt\":9999999999999,\"physicalCreatedAt\":1}")
            .when(valueOperations).get("hot:k");

    TestDto result = redisUtils.getOrLoadHot("hot:k", TestDto.class, 5, () -> {
        fail("loader 不应被调用");
        return null;
    });

    assertEquals(new TestDto("warm"), result);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -Dtest=RedisUtilsTest#staleWithinWindow_returnsStaleValue,RedisUtilsTest#firstMiss_lockFail_retriesCacheBeforeFallback test`
Expected: FAIL because current implementation lacks explicit stale classification and bounded retry policy

- [ ] **Step 3: Implement coordinator workflow**

```java
public <T> T getOrLoad(String key, Class<T> type, HotCacheOptions options, Supplier<T> loader) {
    HotCacheReadResult<T> read = store.read(key, type);
    if (!read.isHit()) {
        return rebuildOrRetry(key, type, options, loader);
    }
    if (read.isFresh()) {
        return read.getData();
    }
    return rebuildOrServeStale(key, type, options, loader, read);
}
```

- [ ] **Step 4: Make `HotCacheService` a thin facade**

```java
public <T> T getOrLoad(String hotKey, Class<T> type, long expireAfter, Supplier<T> loader) {
    HotCacheOptions options = HotCacheOptions.defaults(Duration.ofMinutes(expireAfter));
    return coordinator.getOrLoad(hotKey, type, options, loader);
}
```

- [ ] **Step 5: Run tests and commit**

Run: `./mvnw -Dtest=RedisUtilsTest test`
Expected: PASS for stale, retry, and facade wiring behavior

```bash
git add src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheCoordinator.java \
  src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheService.java \
  src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheStore.java \
  src/main/java/com/xytgy/teamallbackend/cache/multilevel/CacheMetrics.java \
  src/test/java/com/xytgy/teamallbackend/utils/RedisUtilsTest.java
git commit -m "refactor: add coordinated hot cache rebuild flow"
```

## Task 5: Finish observability, compatibility, and cleanup

**Files:**
- Modify: `src/main/java/com/xytgy/teamallbackend/cache/multilevel/CacheMetrics.java`
- Modify: `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheService.java`
- Modify: `src/main/java/com/xytgy/teamallbackend/utils/RedisUtils.java`
- Modify: `src/test/java/com/xytgy/teamallbackend/utils/RedisUtilsTest.java`

- [ ] **Step 1: Write the failing tests for metrics and backward compatibility**

```java
@Test
@DisplayName("旧 API 仍可通过 RedisUtils.getOrLoadHot 访问重构后的热点缓存")
void redisUtils_hotApi_remainsCompatible() {
    doReturn(null).when(valueOperations).get("hot:k");

    TestDto result = redisUtils.getOrLoadHot("hot:k", TestDto.class, 5, () -> new TestDto("fresh"));

    assertEquals(new TestDto("fresh"), result);
}

@Test
@DisplayName("反序列化失败时清理本地缓存并记录失败指标")
void decodeFailure_invalidatesCacheAndRecordsMetric() {
    hotLocalCache.put("hot:k", "bad-json");

    assertNull(redisUtils.getOrLoadHot("hot:k", TestDto.class, 5, () -> null));

    assertNull(hotLocalCache.getIfPresent("hot:k"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -Dtest=RedisUtilsTest#redisUtils_hotApi_remainsCompatible,RedisUtilsTest#decodeFailure_invalidatesCacheAndRecordsMetric test`
Expected: FAIL until metric hooks and final compatibility wiring are complete

- [ ] **Step 3: Finish metrics and compatibility adapter code**

```java
public void recordHotCachedNullHit() { increment(hotCachedNullHit); }
public void recordHotStaleReturn() { increment(hotStaleReturn); }
public void recordHotWriteFailure() { increment(hotWriteFailure); }
public void recordHotDecodeFailure() { increment(hotDecodeFailure); }
```

```java
public <T> T getOrLoadHot(String hotKey, Class<T> type, long expireAfter, Supplier<T> loader) {
    return hotCache.getOrLoad(hotKey, type, expireAfter, loader);
}
```

- [ ] **Step 4: Run full targeted test suite**

Run: `./mvnw -Dtest=RedisUtilsTest test`
Expected: PASS

Run: `./mvnw test`
Expected: PASS or only unrelated pre-existing failures

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/xytgy/teamallbackend/cache/multilevel/CacheMetrics.java \
  src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheService.java \
  src/main/java/com/xytgy/teamallbackend/utils/RedisUtils.java \
  src/test/java/com/xytgy/teamallbackend/utils/RedisUtilsTest.java
git commit -m "refactor: finalize enterprise hot cache refactor"
```

## Self-Review

### Spec coverage

- Explicit null caching: covered in Tasks 1, 2, 4, 5
- Renewable unique-token lock: covered in Task 3
- Unified write path and preload consistency: covered in Task 2
- Bounded stale fallback: covered in Task 4
- Stable external API: covered in Tasks 4 and 5
- Observability: covered in Tasks 3 and 5

### Placeholder scan

- No `TODO`, `TBD`, or “implement later” placeholders remain
- Each task names exact files and commands
- Each code step includes concrete code skeletons

### Type consistency

- `HotCacheRecord`, `HotCacheReadResult`, `HotCacheOptions`, `HotCacheStore`, `HotCacheLockService`, and `HotCacheCoordinator` are used consistently across all tasks
