# Cache Invalidation Enterprise Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复缓存删除的并发、事务和多实例一致性问题，并将核心批量缓存迁移到版本化 Key。

**Architecture:** 通过事务事件将业务失效操作延迟到提交后；`CacheKeyCleaner` 只负责 Redis 删除和本机 L1 清理；Redis Pub/Sub 通知其他实例清理 L1；有限异步重试补偿 Redis 短暂故障。列表、搜索、建议和话题分页使用版本号切换，旧 Key 由 TTL 回收。

**Tech Stack:** Java 17、Spring Boot、Spring Transaction Events、Spring Data Redis、Caffeine、Micrometer、JUnit 5、Mockito

---

### Task 1: 本地缓存失效与安全 Glob

**Files:**
- Create: `src/main/java/com/xytgy/teamallbackend/cache/multilevel/LocalCacheInvalidator.java`
- Create: `src/main/java/com/xytgy/teamallbackend/cache/multilevel/RedisGlobPattern.java`
- Modify: `src/main/java/com/xytgy/teamallbackend/cache/multilevel/MultiLevelCacheService.java`
- Modify: `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheService.java`
- Test: `src/test/java/com/xytgy/teamallbackend/cache/multilevel/RedisGlobPatternTest.java`

- [ ] 将两个 L1 的精确失效和 Pattern 失效集中到 `LocalCacheInvalidator`。
- [ ] Pattern 只编译一次后传入两个本地缓存。
- [ ] 支持 `*`、`?`、`[abc]`、`[^abc]`，并使用完整匹配锚点。
- [ ] 拒绝空 Pattern、单独 `*` 和超过 256 字符的 Pattern。

### Task 2: 多实例失效同步

**Files:**
- Create: `src/main/java/com/xytgy/teamallbackend/cache/multilevel/CacheInvalidationMessage.java`
- Create: `src/main/java/com/xytgy/teamallbackend/cache/multilevel/CacheInvalidationSyncService.java`
- Test: `src/test/java/com/xytgy/teamallbackend/cache/multilevel/CacheInvalidationSyncServiceTest.java`

- [ ] 定义 `KEY`、`PATTERN` 两类失效消息。
- [ ] 复用现有 `RedisMessageListenerContainer` 注册频道。
- [ ] 发布消息时带实例 ID 和事件 ID。
- [ ] 收到其他实例消息后只清理本地 L1，不删除 Redis、不再次广播。
- [ ] 非法消息受控记录并忽略。

### Task 3: Redis 删除与有限重试

**Files:**
- Create: `src/main/java/com/xytgy/teamallbackend/cache/multilevel/CacheInvalidationRetryService.java`
- Modify: `src/main/java/com/xytgy/teamallbackend/cache/multilevel/CacheKeyCleaner.java`
- Modify: `src/main/java/com/xytgy/teamallbackend/cache/multilevel/CacheMetrics.java`
- Test: `src/test/java/com/xytgy/teamallbackend/cache/multilevel/CacheKeyCleanerTest.java`

- [ ] 使用 Spring 管理的有界调度线程池执行最多 3 次指数退避重试。
- [ ] 精确删除顺序调整为 Redis、finally 本地 L1、Pub/Sub。
- [ ] Redis 失败不向业务层抛出，记录指标并提交重试。
- [ ] Pattern 删除继续使用 SCAN 和 100 条批次，记录扫描数、删除数和耗时。
- [ ] Cursor 始终通过 try-with-resources 关闭。

### Task 4: 事务提交后失效

**Files:**
- Create: `src/main/java/com/xytgy/teamallbackend/cache/multilevel/CacheInvalidationEvent.java`
- Create: `src/main/java/com/xytgy/teamallbackend/cache/multilevel/CacheInvalidationEventPublisher.java`
- Create: `src/main/java/com/xytgy/teamallbackend/cache/multilevel/CacheInvalidationEventListener.java`
- Modify: `src/main/java/com/xytgy/teamallbackend/utils/RedisUtils.java`
- Test: `src/test/java/com/xytgy/teamallbackend/cache/multilevel/CacheInvalidationEventListenerTest.java`

- [ ] `RedisUtils.delete()` 和兼容 `deleteByPattern()` 改为发布事件。
- [ ] 监听器使用 `AFTER_COMMIT`，事务回滚不执行。
- [ ] 使用 `fallbackExecution = true`，非事务调用立即执行，避免事件静默丢失。

### Task 5: 版本化 Key

**Files:**
- Create: `src/main/java/com/xytgy/teamallbackend/cache/multilevel/VersionedCacheKeyService.java`
- Modify: `src/main/java/com/xytgy/teamallbackend/utils/RedisUtils.java`
- Modify: `src/main/java/com/xytgy/teamallbackend/module/product/service/ProductSearchService.java`
- Modify: `src/main/java/com/xytgy/teamallbackend/module/product/service/ProductSyncService.java`
- Modify: `src/main/java/com/xytgy/teamallbackend/module/product/service/impl/ProductServiceImpl.java`
- Modify: `src/main/java/com/xytgy/teamallbackend/module/teacircle/service/impl/TeaTopicServiceImpl.java`
- Test: `src/test/java/com/xytgy/teamallbackend/cache/multilevel/VersionedCacheKeyServiceTest.java`

- [ ] 提供稳定的版本读取、版本化 Key 构建和事务提交后版本递增。
- [ ] 商品搜索、建议、热门词改为共享商品搜索版本。
- [ ] 商品同步完成后递增搜索版本，移除相关 `deleteByPattern()`。
- [ ] 话题列表改为版本化 Key，创建话题后递增版本。
- [ ] 商品列表现有版本逻辑迁入统一服务。
- [ ] 为数据库写方法补齐事务边界，保证版本递增发生在提交后。

### Task 6: 回归验证

**Files:**
- Modify: `src/test/java/com/xytgy/teamallbackend/utils/RedisUtilsTest.java`

- [ ] 更新构造依赖和删除测试。
- [ ] 全局搜索确认核心业务不再调用 `deleteByPattern()`。
- [ ] 运行缓存定向测试。
- [ ] 运行 `./mvnw test`，预期全部通过。
