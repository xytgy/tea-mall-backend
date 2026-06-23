# RedisUtils 渐进式拆分重构设计

## 目标

在不修改业务调用方式、不改变缓存行为和 Redis 数据格式的前提下，将当前超过
600 行、承担多种职责的 `RedisUtils` 拆分为职责清晰、可以独立测试的缓存组件。

`RedisUtils` 保留为兼容门面，现有业务代码继续调用：

```java
redisUtils.getOrLoad(...);
redisUtils.set(...);
redisUtils.delete(...);
```

本次重构不要求业务模块切换到新的组件类型。

## 当前问题

现有 `RedisUtils` 同时承担：

- Caffeine L1 本地缓存管理
- Redis L2 分布式缓存管理
- Cache-Aside 回源
- JSON 序列化与反序列化
- 空值缓存与缓存穿透保护
- TTL 抖动
- 布隆过滤器组合
- 热点数据预热与逻辑过期
- 热点缓存分布式锁
- 单 Key 和模式批量删除
- Micrometer 指标注册与记录

这些能力本身有实际价值，但集中在一个类中会导致职责边界模糊、依赖过多、
测试范围过大，并增加局部改动影响其他缓存路径的风险。

## 重构原则

1. 保持 `RedisUtils` 的所有公共方法签名不变。
2. 保持 Redis Key、JSON 格式、TTL 单位和空值占位符不变。
3. 不要求清空或迁移现有 Redis 数据。
4. 不修改业务 Service、Controller 或 Mapper 的调用代码。
5. 按阶段迁移，每个阶段都可独立编译和测试。
6. 新组件之间保持单向依赖，避免形成循环依赖。

## 组件设计

### RedisUtils

定位：兼容门面与组合层。

保留现有公共 API，但不再直接维护普通缓存、热点缓存或指标实现。

主要职责：

- 将普通缓存调用转发给 `MultiLevelCacheService`。
- 将热点缓存调用转发给 `HotCacheService`。
- 将删除操作转发给 `CacheKeyCleaner`。
- 组合 `BloomFilterManager` 与 `MultiLevelCacheService`，保留现有布隆过滤器加载方法。
- 保留调用方依赖的公开 TTL 抖动与缓存统计入口，并委托给对应组件。

不再承担：

- Caffeine 缓存实例创建
- Redis 读写细节
- JSON 序列化
- 热点锁实现
- Micrometer 指标创建
- SCAN 删除实现

### MultiLevelCacheService

定位：普通 L1/L2 两级缓存服务。

负责：

- 创建和持有普通缓存使用的 Caffeine L1。
- 按 `L1 -> Redis` 顺序读取原始缓存值。
- 实现 `get`、`set` 和 `getOrLoad`。
- 实现 Cache-Aside 回源和两级回填。
- 处理 `NULL` 空值占位符。
- 处理普通对象和 `TypeReference` 泛型反序列化。
- 生成带随机抖动的 Redis TTL。
- 在脏缓存反序列化失败时删除 L1 和 Redis 数据。
- 提供普通缓存统计信息。

保持兼容的常量语义：

- 空值占位符：`NULL`
- 缓存未命中哨兵：仅在进程内部使用，不写入 Redis
- 空值 TTL：2 分钟
- 默认 TTL 抖动：正负 20%

### HotCacheService

定位：热点数据缓存服务。

负责：

- 创建和持有热点缓存专用的 Caffeine L1。
- 热点数据预热。
- `HotCacheWrapper` 的序列化与反序列化。
- 逻辑过期判断。
- 获取和释放 Redis 分布式锁。
- 锁竞争失败时返回旧数据。
- Redis 中热点数据的物理 TTL。

热点缓存使用独立 L1，避免普通缓存的物理 TTL 或淘汰策略影响逻辑过期语义。

保持兼容：

- 热点锁 Key 前缀保持 `hot:lock:`。
- 分布式锁过期时间保持 10 秒。
- 热点 Redis 物理 TTL 保持逻辑过期时间的 3 倍。
- `HotCacheWrapper` JSON 字段继续使用 `data` 和 `expireAt`。
- 解锁继续使用 owner 校验 Lua 脚本，不能直接删除其他实例持有的锁。

### CacheKeyCleaner

定位：统一缓存失效服务。

负责：

- 删除普通 L1、热点 L1 和 Redis 中的指定 Key。
- 使用 Redis `SCAN` 按模式分批删除。
- 同步清理两个 L1 中匹配模式的 Key。

保持兼容：

- 不使用可能阻塞 Redis 的 `KEYS`。
- `SCAN` 每批数量保持 100。
- `delete` 和 `deleteByPattern` 的外部行为不变。

为了避免 `CacheKeyCleaner` 直接访问组件私有字段，
`MultiLevelCacheService` 和 `HotCacheService` 分别提供包内可见的失效方法。

### CacheMetrics

定位：缓存指标适配器。

负责初始化和更新：

- `cache.l1.hit`
- `cache.l1.miss`
- `cache.l2.hit`
- `cache.l2.miss`
- `cache.source.load`
- `cache.hot.lock.contended`
- `cache.bloom.blocked`

`MeterRegistry` 不存在时，所有指标方法为空操作，不影响缓存主流程。

业务缓存组件只调用语义化方法，例如：

```java
metrics.recordL1Hit();
metrics.recordL2Miss();
metrics.recordSourceLoad(loader);
```

组件不直接持有或判断具体的 Micrometer Counter。

## 依赖关系

```text
业务 Service
    |
    v
RedisUtils
    |-- MultiLevelCacheService
    |-- HotCacheService
    |-- CacheKeyCleaner
    |-- BloomFilterManager
    `-- CacheMetrics（仅记录布隆拦截）

MultiLevelCacheService --> CacheMetrics
HotCacheService --------> CacheMetrics
CacheKeyCleaner --------> MultiLevelCacheService
CacheKeyCleaner --------> HotCacheService
```

约束：

- `MultiLevelCacheService` 不依赖 `HotCacheService`。
- `HotCacheService` 不依赖 `MultiLevelCacheService`。
- `CacheMetrics` 不依赖任何缓存组件。
- `CacheKeyCleaner` 只调用组件公开或包内失效接口，不读取其内部缓存字段。
- 新组件不反向依赖 `RedisUtils`。

## 数据流

### 普通缓存读取

```text
RedisUtils.getOrLoad
    -> MultiLevelCacheService.getOrLoad
        -> 普通 L1
        -> Redis L2
        -> loader 回源
        -> 同时写入 L1 和 Redis
```

### 布隆过滤器读取

```text
RedisUtils.getOrLoadWithBloomFilter
    -> BloomFilterManager 判断
    -> 不存在：记录 bloom blocked，直接返回 null
    -> 可能存在：MultiLevelCacheService.getOrLoad
```

### 热点缓存读取

```text
RedisUtils.getOrLoadHot
    -> HotCacheService.getOrLoadHot
        -> 热点 L1 / Redis
        -> 未逻辑过期：直接返回
        -> 已过期：尝试获取分布式锁
        -> 获锁：回源并刷新
        -> 未获锁：返回旧数据
```

## 初始化

当前 `RedisUtils.init()` 中的逻辑迁移为：

- `MultiLevelCacheService` 在自身 `@PostConstruct` 中创建普通 L1。
- `HotCacheService` 在自身 `@PostConstruct` 中创建热点 L1。
- `CacheMetrics` 在构造或 `@PostConstruct` 中注册指标。
- `RedisUtils` 不再需要初始化内部缓存。

普通缓存与热点缓存默认沿用 `cache.local.*` 配置。
本次不新增热点缓存专用配置，避免扩大配置迁移范围。

## 迁移顺序

### 第一阶段：指标与普通缓存

1. 提取 `CacheMetrics`。
2. 提取 `MultiLevelCacheService`。
3. 将 `get`、`set`、`getRaw`、`increment`、`getOrLoad`、TTL 抖动和统计转发出去。
4. 迁移普通缓存现有测试。

### 第二阶段：删除能力

1. 提取 `CacheKeyCleaner`。
2. 迁移 `delete` 和 `deleteByPattern`。
3. 验证两个 L1 和 Redis 均被清理。

### 第三阶段：热点缓存

1. 提取 `HotCacheService`。
2. 迁移预热、逻辑过期、锁和旧数据兜底。
3. 保持 `HotCacheWrapper` JSON 兼容。
4. 迁移热点缓存测试。

### 第四阶段：门面收口

1. `RedisUtils` 只保留公共 API 转发和布隆过滤器组合。
2. 删除已迁移的私有实现和无用依赖。
3. 增加门面转发测试。
4. 运行完整测试。

## 异常处理

- Redis 访问异常保持现有传播或降级行为，不在重构中改变语义。
- JSON 序列化失败继续记录错误，不写入损坏缓存。
- JSON 反序列化失败继续删除对应脏缓存。
- 热点缓存锁释放继续使用 Lua owner 校验。
- `MeterRegistry` 不存在时指标为空操作。
- Loader 抛出的业务异常继续向调用方传播，不写入缓存。

## 测试设计

### MultiLevelCacheService

- L1 命中不访问 Redis。
- L1 未命中、L2 命中并回填 L1。
- 两级未命中时执行 loader 并回填。
- 空值占位符阻止重复回源。
- 泛型反序列化。
- 脏缓存清理。
- TTL 抖动范围。

### HotCacheService

- 未逻辑过期数据直接返回。
- 逻辑过期且获得锁时回源刷新。
- 锁竞争失败时返回旧数据。
- 旧数据不存在时直接回源。
- 解锁 Lua owner 校验。
- 热点预热跳过仍有效的数据。

### CacheKeyCleaner

- 单 Key 同时清理两个 L1 和 Redis。
- 模式删除使用 SCAN 分批执行。
- 无匹配 Key 时不执行批量删除。

### CacheMetrics

- 有 MeterRegistry 时正确记录指标。
- 无 MeterRegistry 时不抛异常。
- 回源计时返回原 loader 结果并传播异常。

### RedisUtils

- 公共方法正确转发到目标组件。
- 布隆过滤器拦截时不调用普通缓存。
- 布隆过滤器放行时调用普通缓存并保持返回值。

## 验收标准

1. 现有业务调用方无需修改。
2. `RedisUtils` 变为较薄的兼容门面，不再直接操作普通和热点缓存细节。
3. 普通缓存、热点缓存、缓存删除和指标可以独立测试。
4. Redis Key、JSON 格式、TTL 语义和空值占位符保持兼容。
5. 不清空或迁移现有 Redis 数据。
6. `./mvnw clean test` 全部通过。

## 不在本次范围内

- 将业务调用方迁移到新组件
- 修改现有缓存 Key 命名
- 修改缓存 TTL 数值
- 新增缓存配置中心参数
- 替换 Caffeine、Redis 或 Jackson
- 引入 Spring Cache 抽象
- 修改布隆过滤器实现
- 调整业务缓存一致性策略
