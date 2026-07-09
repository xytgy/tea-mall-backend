# 学习路线图 - tea-mall-backend

## 已完成：秒杀模块

| Phase | 知识点 | 核心代码 |
|-------|--------|----------|
| Phase 1 | Redis Lua 原子扣减库存 | flash_deduct_pratice.lua |
| Phase 2 | 本地缓存 + Redis Pub/Sub 广播 | ConcurrentHashMap + convertAndSend |
| Phase 3 | RocketMQ 异步下单 | FlashOrderPublisher + FlashOrderMqListener |
| Phase 4 | 滑动窗口限流 | sliding_window_limit.lua |
| Phase 5 | 熔断降级（Resilience4j） | @CircuitBreaker + buyFallback |

---

## 待学习模块（推荐顺序）

### 模块1：订单 + 支付（第1步学习）

**核心知识点：**
- 订单状态机（8+状态流转：待支付→已支付→已发货→已完成→退款等）
- 支付宝PC支付集成（RSA签名验证、异步回调）
- 支付幂等性（Redis SETNX 防重复回调）
- 延迟消息自动取消未支付订单（RocketMQ 延迟消息）
- 分布式锁防并发支付确认
- 自注入模式解决 @Transactional 内部调用失效

**关键文件：**
- module/order/service/impl/OrdersServiceImpl.java
- module/order/controller/PaymentController.java
- config/AlipayConfig.java
- mq/handler/OrderTimeoutHandler.java

**学习路径：**
1. 先看订单状态机（状态流转图）
2. 再看支付流程（支付宝集成）
3. 最后看幂等性和延迟取消

#### 重点：支付回调 + 幂等性设计

**为什么需要幂等性？**
支付宝回调可能重复发送（网络超时重试），如果不处理，同一笔支付会确认多次。

**流程：**
```
支付宝异步回调
    ↓
1. RSA签名验证（防伪造）
    ↓
2. Redis SETNX 防重复（key=支付流水号，24h过期）
   ├─ 设置成功 → 继续处理
   └─ 设置失败 → 已处理过，直接返回success
    ↓
3. 校验金额是否一致
    ↓
4. 确认支付（更新订单状态0→1）
    ↓
5. 发MQ通知（库存扣减、通知用户等）
    ↓
6. 返回success给支付宝
```

**关键代码位置：**
- PaymentController.java:162 — 支付宝回调入口
- PaymentController.java:128 — confirmPayment确认支付
- Redis SETNX 幂等key — 防止重复处理

**秒杀模块知识复用：**
- Redis SETNX → 支付幂等
- MQ → 异步通知库存扣减
- 分布式锁 → 防并发支付确认

---

### 模块2：分布式锁（第2步学习）

**核心知识点：**
- Redisson分布式锁原理（SETNX + Lua释放）
- 锁续期（WatchDog自动延长过期时间）
- 可重入锁 vs 不可重入锁
- 锁竞争：等待+重试 vs 快速失败

**关键文件：**
- lock/DistributedLock.java
- lock/LockGuard.java
- lock/LockAcquisitionException.java
- config/RedissonConfig.java

**学习价值：** 面试必问，理解多进程并发控制

---

### 模块3：MQ可靠性（第3步学习）

**核心知识点：**
- 指数退避重试（10s/30s/90s/270s 递增）
- 幂等消费（唯一索引防重复消费）
- Claim-and-PROCESSING 状态防并发处理
- 消息丢失/积压/重复的应对方案

**关键文件：**
- mq/scheduler/MqRetryScheduler.java
- mq/util/IdempotentUtil.java
- mq/entity/MqRetryRecord.java

**学习价值：** 生产环境MQ必问题，面试高频

---

### 模块4：布隆过滤器（第4步学习）

**核心知识点：**
- 布隆过滤器原理（多个哈希函数 + 位数组）
- 为什么能防缓存穿透（快速判断key不存在）
- 误判率控制（expectedInsertions + falsePositiveProbability）
- 定时重建（数据变更后刷新过滤器）

**关键文件：**
- cache/bloom/BloomFilterManager.java
- cache/bloom/BloomFilterRebuildService.java
- cache/bloom/BloomFilterSyncService.java

**学习价值：** 高并发场景防穿透必备，原理面试常考

---

### 模块5：茶圈社交（第5步学习）

**核心知识点：**
- 社交Feed架构（广场 vs 关注，分页加载）
- 点赞防重复（唯一索引 + 乐观并发）
- 异步通知（MQ解耦点赞/评论事件）
- 循环依赖处理（@Lazy注入）

**关键文件：**
- module/teacircle/controller/TeaCirclePostController.java
- module/teacircle/service/impl/TeaPostServiceImpl.java
- mq/publisher/TeaNotificationPublisher.java

---

### 模块6：Elasticsearch商品搜索（第6步学习）

**核心知识点：**
- IK中文分词器（ik_max_word 索引 / ik_smart 搜索）
- 双同步策略（全量异步 + 增量同步）
- 条件激活（@ConditionalOnProperty）

**关键文件：**
- module/product/document/ProductDocument.java
- module/product/service/ProductSyncService.java

---

### 模块7：WebSocket聊天（第7步学习）

**核心知识点：**
- 会话式聊天架构（买家↔商家自动创建会话）
- 未读消息数优化（覆盖索引 + GROUP BY）
- 读写分离注解（@ReadOnly）

**关键文件：**
- module/chat/controller/ChatController.java
- module/chat/service/impl/ChatServiceImpl.java

---

### 模块8：其他架构模式（第8步学习）

- Nacos 动态配置推送
- 异步线程池配置（CallerRunsPolicy / AbortPolicy）
- 条件 Bean 激活（Feature Toggle）

---

## 学习建议

按顺序学习：1→2→3→4→5→6→7→8

1. **订单+支付** — 最复杂、最有价值、面试常问
2. **分布式锁** — 面试必问，理解多进程并发控制
3. **MQ可靠性** — 巩固MQ知识，学习生产级可靠性设计
4. **布隆过滤器** — 高并发场景防穿透必备
5. **茶圈社交** — 学习社交Feed架构设计
6. **ES搜索** — 学习全文搜索引擎集成
7. **WebSocket聊天** — 学习实时通信
8. **其他模式** — 了解Nacos配置、线程池等

---

## 可扩展技术栈（进阶学习）

### 1. Spring Cloud Gateway（API网关）
- 路由转发、负载均衡
- 统一鉴权、限流
- 请求日志、灰度发布
- 学习价值：微服务架构必备

### 2. Sentinel（流量控制）
- QPS限流、热点参数限流
- 熔断降级（比Resilience4j更强大）
- 系统自适应保护
- 学习价值：阿里系标配，生产环境常用

### 3. Seata（分布式事务）
- AT模式（自动补偿）
- TCC模式（手动确认）
- 跨服务数据一致性
- 学习价值：高阶面试题，难度大价值高

### 4. Canal（MySQL→Redis同步）
- 监听MySQL binlog
- 自动同步到Redis/Elasticsearch
- 避免手动同步数据不一致
- 学习价值：解决缓存与数据库一致性问题

### 5. Snowflake/Leaf（分布式ID）
- 全局唯一订单号生成
- 趋势递增、高可用
- 学习价值：替代UUID，数据库索引友好

### 6. Spring Retry（声明式重试）
- @Retryable 注解自动重试
- 替代手写try-catch重试逻辑
- 学习价值：简化代码，与Resilience4j配合

### 7. STOMP（WebSocket高级协议）
- Spring WebSocket官方推荐协议
- 支持发布/订阅、消息确认
- 学习价值：比原始WebSocket更规范

### 8. SkyWalking（APM全链路追踪）
- 比Zipkin更强大的链路追踪
- 性能监控、告警
- 学习价值：生产环境可观测性
