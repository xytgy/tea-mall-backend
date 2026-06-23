# MultiLevelCacheService 分布式锁防击穿设计

## 1. 背景

`MultiLevelCacheService#getOrLoad()` 当前按照 L1 Caffeine、L2 Redis、数据源的顺序读取数据。

当 L1 和 L2 同时未命中时，多个应用实例可能同时执行 `loader.get()`。如果该 loader
访问数据库，同一个缓存 key 在过期瞬间可能产生大量重复查询，形成缓存击穿。

本次改造为所有 `getOrLoad()` 请求增加基于 Redisson 的分布式锁保护，同时保持现有
公开方法、缓存 key、TTL、空值保护和异常传播行为不变。

## 2. 目标

- 同一缓存 key 并发未命中时，优先只允许一个请求执行 loader。
- 支持多应用实例之间互斥，而不只是在单个 JVM 内互斥。
- 获取锁后执行二次缓存检查，避免重复回源。
- 锁竞争超时或锁服务异常时允许直接回源，优先保证接口可用。
- 复用项目已有 `DistributedLock` 和 Redisson Watch Dog，不重复实现锁续期与安全释放。

## 3. 非目标

- 不改变 `RedisUtils` 或 `MultiLevelCacheService` 的公开方法签名。
- 不改变普通缓存 key、JSON 格式、TTL 抖动和 `NULL` 空值占位规则。
- 不将普通缓存改造成热点缓存的逻辑过期模式。
- 不保证任何异常场景下 loader 绝对只执行一次；降级回源以业务可用性为优先。
- 不重构 `HotCacheService` 当前的锁实现。

## 4. 方案选择

采用现有 `DistributedLock`。

`MultiLevelCacheService` 注入 `DistributedLock`，以 `cache:load:` 加业务缓存 key
作为锁的业务标识。`DistributedLock` 内部还会添加 `lock:` 前缀，因此实际 Redisson
锁名称类似：

```text
lock:cache:load:product:detail:123
```

选择原因：

- 项目已经引入 Redisson，无需增加依赖。
- `DistributedLock#tryLock()` 最多等待 100ms，符合本次确认的等待策略。
- 未指定租约时间时启用 Watch Dog，可在 loader 执行较慢时自动续期。
- `unlock()` 会检查当前线程是否持锁，避免错误释放其他请求的锁。

## 5. 数据流程

### 5.1 首次缓存检查

`getOrLoadInternal()` 先执行现有 L1 → L2 查询：

1. L1 命中：直接反序列化并返回。
2. L1 未命中、L2 命中：回填 L1 后返回。
3. 两级均未命中：进入分布式锁保护流程。

### 5.2 获取锁成功

1. 获取 `cache:load:{cacheKey}` 对应的 Redisson 锁。
2. 清除 L1 中该 key 的未命中哨兵。
3. 再次执行 L1 → L2 查询。
4. 二次检查命中：直接返回，不执行 loader。
5. 二次检查仍未命中：执行 loader，写入 L1 和 Redis。
6. 在 `finally` 中释放锁。

二次检查是必要的：当前请求等待锁期间，之前持锁的实例可能已经完成缓存回填。

### 5.3 获取锁失败

获取锁最多等待 100ms。失败后：

1. 清除 L1 中该 key 的未命中哨兵。
2. 再次检查缓存。
3. 如果其他实例已经完成回填，直接返回。
4. 如果仍未命中，降级为直接执行 loader 并写回缓存。

降级回源不能完全保证 loader 单次执行，但可以避免请求长时间阻塞或直接失败。

### 5.4 锁服务异常

`DistributedLock#tryLock()` 当前会捕获中断并返回 false。Redisson 抛出的其他运行时异常
可能继续向上传播，因此 `MultiLevelCacheService` 调用锁时负责捕获运行时异常、记录警告，
并将其视为锁竞争失败，执行相同的二次检查与降级回源。

缓存系统故障不应阻止核心业务访问数据库。

## 6. L1 未命中哨兵处理

当前 `getCachedJson()` 使用 `CACHE_MISS_SENTINEL` 表示 Redis 未命中，并将其暂存在
Caffeine 中。这样可以满足 Caffeine 加载函数不能返回 null 的限制。

但如果二次检查直接调用 `getCachedJson()`，它可能继续读到 L1 中的旧哨兵，而不访问
已经被其他实例回填的 Redis。

因此每次锁后的二次检查前，必须先执行：

```text
localCache.invalidate(key)
```

然后重新执行 L1 → L2 查询。

## 7. 组件改动

### MultiLevelCacheService

- 新增 `DistributedLock` 构造器依赖，由 `@RequiredArgsConstructor` 注入。
- `getOrLoadInternal()` 在两级缓存未命中后进入带锁加载流程。
- 新增私有方法封装：
  - 锁 key 生成。
  - 清除哨兵后的二次缓存检查。
  - loader 回源和两级缓存回填。
- `Class<T>` 与 `TypeReference<T>` 两个重载继续共用同一模板流程。

### DistributedLock

优先不修改。复用其现有 100ms 等待时间、Watch Dog 和安全解锁能力。

### CacheMetrics

本次不增加普通缓存锁竞争指标，只保留现有回源计时。锁服务异常通过警告日志观察，
锁竞争失败属于预期分支，不逐次输出警告日志。

## 8. 异常处理

- loader 抛出运行时异常：原样向上抛出，不写入缓存。
- loader 抛异常且当前请求持锁：必须在 `finally` 中释放锁。
- 序列化失败：保持现有行为，记录错误且不写入无效 JSON。
- Redis 二次检查失败：遵循现有 Redis 客户端异常行为，不吞掉与锁无关的数据访问异常。
- 获取锁失败或被中断：执行二次检查，仍未命中则直接回源。

## 9. 兼容性

以下行为保持不变：

- `RedisUtils#getOrLoad()` 调用方式。
- `MultiLevelCacheService#getOrLoad()` 两种重载。
- Redis 业务缓存 key。
- 正常数据 JSON 格式。
- `NULL` 空值占位符和 2 分钟空值 TTL。
- 正常数据 TTL 抖动。
- loader 异常向调用方传播。

## 10. 测试设计

在现有缓存测试基础上增加：

1. 首次缓存未命中且抢锁成功，执行 loader、回填缓存并释放锁。
2. 抢锁成功后二次检查命中，不执行 loader。
3. 抢锁失败后二次检查命中，直接返回缓存结果。
4. 抢锁失败且二次检查仍未命中，降级执行 loader。
5. loader 抛异常时仍释放锁，并且不写入缓存。
6. loader 返回 null 时仍写入 `NULL` 占位符。
7. `Class<T>` 和 `TypeReference<T>` 两个重载都经过相同的锁保护。
8. 原有 L1 命中和 L2 命中场景不尝试获取锁。

完成后运行：

```bash
MAVEN_USER_HOME=/tmp/tea-mall-m2 ./mvnw clean test
```

验收标准为全部现有测试及新增测试通过。
