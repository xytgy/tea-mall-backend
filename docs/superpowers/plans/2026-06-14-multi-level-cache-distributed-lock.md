# MultiLevelCacheService Distributed Lock Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为普通两级缓存的所有 `getOrLoad()` 请求增加 Redisson 分布式锁、二次缓存检查和可用性优先的降级回源，减少多实例缓存击穿。

**Architecture:** `MultiLevelCacheService` 复用现有 `DistributedLock`。两级缓存首次未命中后尝试等待锁 100ms；无论拿锁成功还是失败，都先清除 L1 未命中哨兵并重新查询 Redis，只有二次检查仍未命中时才执行 loader。拿锁成功的分支必须在 `finally` 中释放锁，锁异常和竞争失败则降级回源。

**Tech Stack:** Java 17、Spring Boot 3.3.4、Caffeine、Spring Data Redis、Redisson 3.31.0、JUnit 5、Mockito、Maven Wrapper

---

## File Structure

- Modify: `src/main/java/com/xytgy/teamallbackend/cache/MultiLevelCacheService.java`
  - 注入 `DistributedLock`。
  - 实现锁保护、二次缓存检查和降级回源。
- Modify: `src/test/java/com/xytgy/teamallbackend/utils/RedisUtilsTest.java`
  - 增加锁 Mock。
  - 覆盖锁成功、锁失败、锁异常、二次命中、空值命中和异常解锁。
- Reference: `src/main/java/com/xytgy/teamallbackend/lock/DistributedLock.java`
  - 复用现有 100ms 等待、Watch Dog 和安全解锁，不修改该文件。
- Verify: `docs/superpowers/specs/2026-06-14-multi-level-cache-distributed-lock-design.md`
  - 实现必须符合已确认设计。

### Task 1: 为测试夹具增加分布式锁依赖

**Files:**
- Modify: `src/test/java/com/xytgy/teamallbackend/utils/RedisUtilsTest.java`

- [ ] **Step 1: 增加 DistributedLock Mock**

```java
import com.xytgy.teamallbackend.lock.DistributedLock;

@Mock
private DistributedLock distributedLock;
```

- [ ] **Step 2: 将 Mock 注入 MultiLevelCacheService**

```java
multiLevelCacheService = new MultiLevelCacheService(
        stringRedisTemplate, objectMapper, props, metrics, distributedLock);
lenient().when(distributedLock.tryLock(anyString())).thenReturn(true);
```

- [ ] **Step 3: 编译测试并确认生产代码尚未支持新构造参数**

Run:

```bash
MAVEN_USER_HOME=/tmp/tea-mall-m2 ./mvnw -DskipTests test-compile
```

Expected: FAIL，提示 `MultiLevelCacheService` 构造函数参数数量不匹配。

- [ ] **Step 4: 提交测试夹具**

当前工作区不是 Git 仓库，因此记录该逻辑提交点但不执行 `git commit`。在 Git 仓库中对应提交应为：

```bash
git add src/test/java/com/xytgy/teamallbackend/utils/RedisUtilsTest.java
git commit -m "test: prepare cache distributed lock coverage"
```

### Task 2: 编写锁保护行为测试

**Files:**
- Modify: `src/test/java/com/xytgy/teamallbackend/utils/RedisUtilsTest.java`

- [ ] **Step 1: 测试抢锁成功后回源并释放锁**

```java
@Test
void cacheMiss_callsLoaderAndCaches() {
    doReturn(null).when(valueOperations).get("k");

    TestDto result = redisUtils.getOrLoad(
            "k", TestDto.class, 5, () -> new TestDto("loaded"));

    assertEquals(new TestDto("loaded"), result);
    verify(distributedLock).tryLock("cache:load:k");
    verify(distributedLock).unlock("cache:load:k");
    verify(valueOperations).set(
            eq("k"), contains("loaded"), anyLong(), eq(TimeUnit.MINUTES));
}
```

- [ ] **Step 2: 测试抢锁成功后二次检查命中**

```java
@Test
void lockSuccess_secondCheckHit_skipsLoader() {
    doReturn(null, "{\"name\":\"filled\"}").when(valueOperations).get("k");

    TestDto result = redisUtils.getOrLoad("k", TestDto.class, 5, () -> {
        fail("其他实例已回填缓存，不应调用 loader");
        return null;
    });

    assertEquals(new TestDto("filled"), result);
    verify(distributedLock).unlock("cache:load:k");
    verify(valueOperations, never()).set(eq("k"), anyString(), anyLong(), any());
}
```

- [ ] **Step 3: 测试抢锁失败时的二次命中与降级回源**

```java
@Test
void lockFail_secondCheckHit_returnsCachedValue() {
    doReturn(false).when(distributedLock).tryLock("cache:load:k");
    doReturn(null, "{\"name\":\"filled\"}").when(valueOperations).get("k");

    TestDto result = redisUtils.getOrLoad("k", TestDto.class, 5, () -> {
        fail("其他实例已回填缓存，不应调用 loader");
        return null;
    });

    assertEquals(new TestDto("filled"), result);
    verify(distributedLock, never()).unlock(anyString());
}

@Test
void lockFail_secondCheckMiss_fallsBackToLoader() {
    doReturn(false).when(distributedLock).tryLock("cache:load:k");
    doReturn(null).when(valueOperations).get("k");

    TestDto result = redisUtils.getOrLoad(
            "k", TestDto.class, 5, () -> new TestDto("fallback"));

    assertEquals(new TestDto("fallback"), result);
    verify(distributedLock, never()).unlock(anyString());
}
```

- [ ] **Step 4: 测试锁异常、NULL 占位符和 loader 异常释放**

```java
@Test
void lockThrows_fallsBackToLoader() {
    doThrow(new IllegalStateException("redis unavailable"))
            .when(distributedLock).tryLock("cache:load:k");
    doReturn(null).when(valueOperations).get("k");

    TestDto result = redisUtils.getOrLoad(
            "k", TestDto.class, 5, () -> new TestDto("fallback"));

    assertEquals(new TestDto("fallback"), result);
    verify(distributedLock, never()).unlock(anyString());
}

@Test
void secondCheckNullPlaceholder_skipsLoader() {
    doReturn(null, "NULL").when(valueOperations).get("k");

    assertNull(redisUtils.getOrLoad("k", TestDto.class, 5, () -> {
        fail("空值缓存已经回填，不应调用 loader");
        return null;
    }));
}
```

在现有 `loaderThrows_propagatesAndNoDirtyCache()` 中增加：

```java
verify(distributedLock).unlock("cache:load:k");
```

- [ ] **Step 5: 测试 TypeReference 重载同样受保护**

```java
@Test
void typeReferenceMiss_usesDistributedLock() {
    doReturn(null).when(valueOperations).get("k");

    TestDto result = redisUtils.getOrLoad(
            "k", new TypeReference<TestDto>() {}, 5,
            () -> new TestDto("loaded"));

    assertEquals(new TestDto("loaded"), result);
    verify(distributedLock).tryLock("cache:load:k");
    verify(distributedLock).unlock("cache:load:k");
}
```

- [ ] **Step 6: 运行测试并确认行为尚未实现**

Run:

```bash
MAVEN_USER_HOME=/tmp/tea-mall-m2 ./mvnw -Dtest=RedisUtilsTest test
```

Expected: FAIL，锁交互测试未满足或构造函数尚未支持 `DistributedLock`。

### Task 3: 实现分布式锁加载流程

**Files:**
- Modify: `src/main/java/com/xytgy/teamallbackend/cache/MultiLevelCacheService.java`

- [ ] **Step 1: 注入 DistributedLock 并定义锁前缀**

```java
import com.xytgy.teamallbackend.lock.DistributedLock;

private static final String LOAD_LOCK_PREFIX = "cache:load:";

private final DistributedLock distributedLock;
```

`@RequiredArgsConstructor` 会自动将该依赖加入 Spring 构造器。

- [ ] **Step 2: 将首次未命中转入锁保护方法**

```java
private <T> T getOrLoadInternal(String key, Function<String, T> deserializer,
                                long ttlMinutes, Supplier<T> loader) {
    String json = getCachedJson(key);
    if (json != null) {
        return NULL_PLACEHOLDER.equals(json) ? null : deserializer.apply(json);
    }

    return loadWithDistributedLock(key, deserializer, ttlMinutes, loader);
}
```

- [ ] **Step 3: 实现锁获取、异常降级和 finally 解锁**

```java
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
```

- [ ] **Step 4: 实现哨兵清理、二次检查和回源**

```java
private <T> T retryOrLoad(String key, Function<String, T> deserializer,
                          long ttlMinutes, Supplier<T> loader) {
    String json = retryCachedJson(key);
    if (json != null) {
        return NULL_PLACEHOLDER.equals(json) ? null : deserializer.apply(json);
    }
    return loadAndCache(key, ttlMinutes, loader);
}

private String retryCachedJson(String key) {
    localCache.invalidate(key);
    return getCachedJson(key);
}

private <T> T loadAndCache(String key, long ttlMinutes, Supplier<T> loader) {
    T result = metrics.recordSourceLoad(loader);
    writeBoth(key, result, ttlMinutes);
    return result;
}
```

- [ ] **Step 5: 运行缓存测试**

Run:

```bash
MAVEN_USER_HOME=/tmp/tea-mall-m2 ./mvnw -Dtest=RedisUtilsTest test
```

Expected: PASS，`Tests run: 536, Failures: 0, Errors: 0`。

- [ ] **Step 6: 提交核心实现**

当前工作区不是 Git 仓库，因此记录该逻辑提交点但不执行 `git commit`。在 Git 仓库中对应提交应为：

```bash
git add src/main/java/com/xytgy/teamallbackend/cache/MultiLevelCacheService.java \
        src/test/java/com/xytgy/teamallbackend/utils/RedisUtilsTest.java
git commit -m "fix: prevent multi-instance cache breakdown"
```

### Task 4: 全量回归与设计核对

**Files:**
- Verify: `src/main/java/com/xytgy/teamallbackend/cache/MultiLevelCacheService.java`
- Verify: `src/test/java/com/xytgy/teamallbackend/utils/RedisUtilsTest.java`
- Verify: `docs/superpowers/specs/2026-06-14-multi-level-cache-distributed-lock-design.md`

- [ ] **Step 1: 检查首次命中路径不获取锁**

确认以下代码仍位于锁逻辑之前：

```java
String json = getCachedJson(key);
if (json != null) {
    return NULL_PLACEHOLDER.equals(json) ? null : deserializer.apply(json);
}
```

- [ ] **Step 2: 检查兼容性**

确认未修改：

```text
RedisUtils#getOrLoad 方法签名
MultiLevelCacheService#getOrLoad 方法签名
Redis 业务缓存 key
NULL 占位符
空值 TTL 2 分钟
正常 TTL 抖动
```

- [ ] **Step 3: 运行完整测试**

Run:

```bash
MAVEN_USER_HOME=/tmp/tea-mall-m2 ./mvnw clean test
```

Expected: BUILD SUCCESS，全部测试 0 failures、0 errors。

- [ ] **Step 4: 检查没有遗留占位或调试代码**

Run:

```bash
rg -n "TODO|TBD|System\\.out|printStackTrace" \
  src/main/java/com/xytgy/teamallbackend/cache/MultiLevelCacheService.java \
  src/test/java/com/xytgy/teamallbackend/utils/RedisUtilsTest.java
```

Expected: 无输出。
