# BloomFilter 企业级加固实施计划

**目标：** 修复 BloomFilter 初始化失败误拦截、用户过滤器未初始化、多实例数据不同步、重建覆盖并发写入和全量加载等问题，同时保持现有公开调用方式不变。

## Task 1：配置与分页查询基础设施

**文件：**
- 修改 `src/main/java/com/xytgy/teamallbackend/properties/CacheProperties.java`
- 修改 `src/main/resources/application.yaml`
- 修改 `src/main/java/com/xytgy/teamallbackend/mapper/ProductMapper.java`
- 修改 `src/main/java/com/xytgy/teamallbackend/mapper/UserMapper.java`

**步骤：**
1. 增加 BloomFilter 容量、误判率、分页大小和重建周期配置。
2. 为商品和用户 Mapper 增加基于主键游标的 ID 分页查询。
3. 查询仅返回未逻辑删除的 ID，避免加载完整实体和深分页。

## Task 2：重构 BloomFilterManager

**文件：**
- 重写 `src/main/java/com/xytgy/teamallbackend/cache/BloomFilterManager.java`

**步骤：**
1. 使用 `AtomicReference` 保存不可变状态对象，明确 READY/NOT_READY。
2. 未就绪时采用 fail-open，防止初始化故障误拦截正常请求。
3. 使用 `BloomFilter<Long>` 避免每次查询创建字符串。
4. 商品和用户分别使用短临界区锁，读取路径保持无锁。
5. 用 pending ID 集合合并重建期间新增数据，保证原子替换不会覆盖并发写入。
6. 保留原有公开方法，降低调用方改造范围。

## Task 3：异步分页初始化和周期重建

**文件：**
- 新增 `src/main/java/com/xytgy/teamallbackend/cache/BloomFilterRebuildService.java`

**步骤：**
1. 应用启动完成后通过现有 `asyncExecutor` 异步初始化。
2. 商品、用户独立分页构建和独立失败处理。
3. 使用游标分页，每批默认 5000 条。
4. 构建成功后原子替换；失败时保留旧过滤器。
5. 每 30 分钟执行一次全量重建，弥补 Pub/Sub 丢消息。
6. 使用单实例重建互斥标记，防止定时任务重叠。

## Task 4：事务提交后同步新增 ID

**文件：**
- 新增 `src/main/java/com/xytgy/teamallbackend/cache/event/ProductCreatedEvent.java`
- 新增 `src/main/java/com/xytgy/teamallbackend/cache/event/UserCreatedEvent.java`
- 新增 `src/main/java/com/xytgy/teamallbackend/cache/BloomFilterSyncMessage.java`
- 新增 `src/main/java/com/xytgy/teamallbackend/cache/BloomFilterSyncService.java`
- 修改 `src/main/java/com/xytgy/teamallbackend/service/impl/ProductServiceImpl.java`
- 修改 `src/main/java/com/xytgy/teamallbackend/service/impl/UserServiceImpl.java`

**步骤：**
1. 商品和用户创建方法加入事务。
2. 保存成功后发布领域事件。
3. 使用 `@TransactionalEventListener(AFTER_COMMIT)`，只同步已提交数据。
4. 本机先写入 BloomFilter，再通过 Redis Pub/Sub 广播。
5. 复用项目现有 `RedisMessageListenerContainer`，避免重复连接资源。
6. 消息异常只降级记录，不影响业务事务。

## Task 5：测试与验证

**文件：**
- 修改 `src/test/java/com/xytgy/teamallbackend/utils/BloomFilterManagerTest.java`
- 新增 `src/test/java/com/xytgy/teamallbackend/cache/BloomFilterRebuildServiceTest.java`
- 新增 `src/test/java/com/xytgy/teamallbackend/cache/BloomFilterSyncServiceTest.java`

**步骤：**
1. 覆盖未就绪 fail-open、空 ID、正常命中和 clear 行为。
2. 覆盖分页构建、商品与用户独立失败、并发新增与重建合并。
3. 覆盖本地新增、Redis 消息消费和非法消息容错。
4. 运行目标测试，再运行项目编译或完整测试。

**验证命令：**

```bash
./mvnw -Dtest=BloomFilterManagerTest,BloomFilterRebuildServiceTest,BloomFilterSyncServiceTest test
./mvnw test
```
