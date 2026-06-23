# BloomFilter 企业级加固设计

## 1. 背景

当前 `BloomFilterManager` 在 `@PostConstruct` 中同步创建商品、用户过滤器，并通过
`productMapper.selectList(null)` 一次性加载全部商品实体。

现有实现存在以下生产风险：

- 商品初始化失败后空过滤器继续工作，会把所有合法商品判定为不存在。
- 用户过滤器从未加载数据库数据，启用后会误拦截全部合法用户。
- 新增商品、用户没有同步到过滤器。
- 全量查询完整实体导致启动慢、内存峰值和数据库压力。
- `clearAll()` 直接替换非 volatile 引用，与并发添加存在数据丢失风险。
- 多实例之间没有新增 ID 同步机制。

本次加固覆盖商品和用户过滤器的完整生命周期：异步初始化、分页重建、故障放行、
事务提交后同步、Redis Pub/Sub 广播和定时纠偏。

## 2. 目标

- 过滤器未就绪或首次构建失败时全部放行，不误拦截合法请求。
- 商品和用户都从数据库分页构建。
- 只查询 ID，使用基于主键的游标分页，避免完整实体和深分页。
- 构建完成后原子替换过滤器状态，构建期间旧状态继续服务。
- 商品、用户事务提交后向所有应用实例同步新增 ID。
- Redis Pub/Sub 漏消息时，通过每 30 分钟全量重建纠偏。
- 重建期间接收到的新增 ID 在替换后仍然保留。
- 保持现有 `BloomFilterManager` 查询与添加方法兼容。

## 3. 非目标

- 不引入 RedisBloom 模块。
- 不支持从标准 BloomFilter 中删除单个 ID。
- 不因商品下架、审核状态变化或用户禁用而删除 ID。
- 不修改缓存 key、TTL 或普通缓存逻辑。
- 不将 Pub/Sub 改为 Redis Stream 或消息队列。
- 不保证 Pub/Sub 消息持久化；可靠性由定时重建兜底。

## 4. 方案比较

### 方案 A：本地过滤器 + Redis Pub/Sub + 定时重建

优点：

- 查询全部在 JVM 内完成，延迟低。
- 能快速同步多实例新增 ID。
- 定时重建可以修正实例离线期间漏掉的消息。
- 复用项目已有 Redis、异步线程池和定时任务能力。

缺点：

- 需要管理本地状态、广播和重建之间的并发。
- Pub/Sub 本身不持久化。

### 方案 B：本地过滤器 + 定时重建

优点是结构简单；缺点是其他实例最长 30 分钟无法感知新增 ID。若过滤器已经 READY，
这段时间内会误拦截新数据。

### 方案 C：RedisBloom 集中式过滤器

多实例一致性最好，但要求 Redis 安装额外模块，每次判断需要网络调用，并改变现有部署
和运维约束。

采用方案 A。

## 5. 组件职责

### 5.1 BloomFilterManager

只负责运行时过滤器状态：

- `mightContainProduct()`、`mightContainUser()`。
- 单个和批量添加商品、用户 ID。
- 保存商品、用户的当前状态快照。
- 将完整构建的新过滤器原子替换为正式状态。
- 管理重建期间新增 ID 的并发增量集合。

本类不再查询数据库，也不负责 Redis 消息发布。

每类过滤器使用独立状态：

```java
record BloomFilterState<T>(BloomFilter<T> filter, boolean ready) {}
```

状态通过 `AtomicReference` 保存。

商品和用户各自使用一把短临界区锁，仅保护“添加 ID”和“最终合并并切换状态”。
`mightContain` 查询不加锁，不影响高并发读取。

### 5.2 BloomFilterRebuildService

负责数据库构建与周期重建：

- 应用启动完成后使用 `@Async("asyncExecutor")` 执行首次构建。
- 商品和用户分别基于 ID 游标分页查询。
- 在临时过滤器中构建完整数据。
- 合并构建期间收到的新增 ID。
- 商品、用户分别在各自完整构建成功后原子替换，互不影响。
- 使用 `@Scheduled` 每 30 分钟执行重建。
- 防止同一实例内启动构建与定时构建重叠。

### 5.3 BloomFilterSyncService

负责事务事件和多实例同步：

- 监听 `ProductCreatedEvent`、`UserCreatedEvent`。
- 使用 `@TransactionalEventListener(phase = AFTER_COMMIT)`，确保事务提交成功后才处理。
- 先更新当前实例过滤器，再向 Redis Channel 发布 JSON 消息。
- 监听 Redis Channel，将其他实例的消息写入本地过滤器。
- 消息格式错误时记录警告并忽略。
- Redis 发布失败时保留当前实例更新，并等待定时重建修正其他实例。

### 5.4 Mapper

`ProductMapper`、`UserMapper` 新增只查询主键的游标分页方法：

```sql
SELECT id
FROM product
WHERE id > #{lastId}
  AND is_deleted = 0
ORDER BY id
LIMIT #{pageSize}
```

用户查询采用同样形式。

过滤器保存所有未逻辑删除的 ID，不按状态、审核状态过滤。状态失效由业务层继续判断，
这样不会因状态变化产生误拦截。

## 6. Fail-open 语义

查询规则：

```text
ID 为 null                    → false
过滤器未就绪或状态不存在       → true
过滤器已就绪                  → filter.mightContain(id)
```

`true` 只表示允许继续访问缓存或数据库，不代表数据一定存在。

首次构建失败时保持未就绪状态，因此所有非空 ID 都放行。不能使用空过滤器作为 READY
状态，否则会产生全量误拦截。

定时重建失败时保留旧的 READY 状态，不将系统降级为空过滤器。

## 7. 启动与定时重建流程

### 7.1 首次构建

1. Spring 应用完成启动。
2. 异步任务分别创建商品、用户临时过滤器。
3. 从 `lastId = 0` 开始，每页查询 5000 个 ID。
4. 将 ID 写入临时过滤器，更新 `lastId` 为本页最大 ID。
5. 页面不足 5000 条或为空时结束。
6. 合并重建期间记录的增量 ID。
7. 原子替换对应正式过滤器并标记 READY。

商品构建成功而用户构建失败时，两类状态独立：商品正常过滤，用户继续全部放行。

### 7.2 定时重建

每 30 分钟触发一次。重建开始前通过原子标记防止同一实例重复运行。

构建期间旧过滤器继续服务。只有某类过滤器完整构建成功时才替换该类状态；失败时保留
旧状态并记录完整异常堆栈。

## 8. 重建期间增量合并

商品和用户分别维护并发增量集合与切换锁：

```java
Set<Long> productPendingIds = ConcurrentHashMap.newKeySet();
Set<Long> userPendingIds = ConcurrentHashMap.newKeySet();
Lock productMutationLock = new ReentrantLock();
Lock userMutationLock = new ReentrantLock();
```

每次 `addProductId()` 或 `addUserId()`：

1. 获取对应类型的 mutation lock。
2. 将 ID 添加到当前 READY 过滤器。
3. 同时写入对应增量集合。
4. 释放锁。

重建替换流程：

1. 数据库分页在锁外完成，避免长时间阻塞新增同步。
2. 获取对应类型的 mutation lock。
3. 将增量集合中的全部 ID 写入临时过滤器。
4. 原子替换正式状态。
5. 清空已经合并的增量集合。
6. 释放锁。

添加 ID 与最终切换使用同一把锁，因此不存在“增量快照完成后、新增 ID 写入旧过滤器但未写入
新过滤器”的窗口。锁只覆盖内存操作，不覆盖数据库分页查询。

## 9. 新增数据同步

### 9.1 事务事件

商品和用户保存成功后发布领域事件：

```java
new ProductCreatedEvent(productId)
new UserCreatedEvent(userId)
```

Service 在 `save()` 成功并获得主键后立即发布领域事件；真正的同步监听器仅在事务提交后触发。

相关创建方法增加明确的 `@Transactional` 边界。事件在事务内部发布，但
`@TransactionalEventListener(phase = AFTER_COMMIT)` 只会在提交成功后更新过滤器并广播；
事务回滚时不得更新过滤器或发布 Redis 消息。

### 9.2 Redis 消息

统一 Channel：

```text
cache:bloom-filter:sync
```

消息 JSON：

```json
{
  "type": "PRODUCT",
  "id": 1001
}
```

`type` 仅允许 `PRODUCT` 和 `USER`，`id` 必须为正数。

本实例先本地添加，再发布消息。收到自己发布的消息时重复添加是幂等的。

## 10. Redis 监听配置

复用 Spring Data Redis 的 `RedisMessageListenerContainer` 模式。

项目已经由 `RedisPubSubConfig` 提供共享 `RedisMessageListenerContainer` Bean。
BloomFilter Listener 直接注入并注册 `cache:bloom-filter:sync` Channel，不新建容器。

监听方法负责：

- UTF-8 解码消息。
- Jackson 反序列化。
- 校验 type 和 id。
- 调用 `BloomFilterManager` 添加 ID。
- 捕获消息解析异常，防止监听线程退出。

## 11. 配置

新增配置前缀：

```yaml
cache:
  bloom:
    product-expected-insertions: 1000000
    user-expected-insertions: 1000000
    false-positive-probability: 0.01
    rebuild-page-size: 5000
    rebuild-fixed-delay: 30m
```

通过 `@ConfigurationProperties` 映射并进行校验：

- expected insertions > 0
- 0 < false positive probability < 1
- page size > 0
- rebuild delay > 0

`BloomFilterManager` 和重建服务不写死容量及误判率。

## 12. 异常处理与可观测性

- 首次构建异常：记录完整堆栈，保持未就绪，全部放行。
- 定时重建异常：记录完整堆栈，保留旧过滤器。
- Redis 发布异常：记录完整堆栈，当前实例继续生效。
- Redis 消息异常：记录消息摘要和异常，不打印敏感内容。
- 单页查询异常：终止该类过滤器本轮构建，不发布不完整状态。

至少记录：

- 每类过滤器构建开始、成功、失败。
- 加载 ID 总数和耗时。
- 当前 READY/DEGRADED 状态。
- Pub/Sub 发布失败次数或警告日志。

本次不强制新增 Micrometer 指标，先保证正确性并保留结构扩展点。

## 13. 兼容性

保留以下公开方法：

- `addProductId`
- `addProductIds`
- `mightContainProduct`
- `addUserId`
- `addUserIds`
- `mightContainUser`
- `clearAll`
- `getExpectedProductInsertions`
- `getExpectedUserInsertions`

`clearAll()` 调整为原子替换未就绪状态，主要用于测试和运维；清空后查询非空 ID 将全部
放行，不再返回 false。

## 14. 测试设计

### BloomFilterManager

- 未初始化时非空 ID 全部放行，null 返回 false。
- READY 后存在 ID 返回 true。
- READY 后未添加 ID 通常返回 false。
- 原子替换不暴露构建中的临时过滤器。
- `clearAll()` 后进入未就绪放行状态。
- 并发查询、添加和替换不抛异常。
- 重建期间增量在替换后仍存在。

### BloomFilterRebuildService

- 商品和用户按 ID 游标分页构建。
- 查询参数正确推进 `lastId`。
- 首次构建失败时保持未就绪。
- 定时重建失败时保留旧状态。
- 商品和用户独立成功或失败。
- 重复重建请求不会在同一实例并发执行。

### BloomFilterSyncService

- AFTER_COMMIT 后本地添加并发布消息。
- 事务回滚不更新过滤器。
- Redis 发布失败时本地仍生效。
- 正确消息更新商品或用户过滤器。
- 非法 type、null/非正 ID、错误 JSON 被忽略。

### 业务创建链路

- 商品创建成功发布 `ProductCreatedEvent`。
- 用户注册和管理员新增用户发布 `UserCreatedEvent`。
- 保存失败时不发布事件。

### 回归

执行：

```bash
MAVEN_USER_HOME=/tmp/tea-mall-m2 ./mvnw clean test
```

全部测试必须为 0 failures、0 errors。

## 15. 面试追问覆盖

实现完成后的说明文档需覆盖：

- 为什么初始化失败必须 fail-open。
- BloomFilter 为什么不能删除，以及删除数据如何处理。
- Pub/Sub 丢消息如何补偿。
- 为什么使用游标分页而不是 OFFSET。
- 如何避免重建覆盖并发新增 ID。
- 本地 BloomFilter 与 RedisBloom 的取舍。
- 事务提交后事件为什么优于保存后直接广播。
