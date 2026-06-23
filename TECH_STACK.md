# 茶叶电商后端项目技术栈

## 一、核心框架与语言

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | LTS 版本，支持 Records、Pattern Matching、Sealed Classes 等新特性 |
| Spring Boot | 3.3.4 | 主框架，内嵌 Tomcat，自动配置 |
| Spring Cloud | 2023.0.1 | 微服务治理 |
| Spring Cloud Alibaba | 2023.0.1.0 | 阿里巴巴微服务组件 |
| Maven | 3.9+ | 项目构建与依赖管理 |

## 二、中间件与基础设施

| 中间件 | 版本 | 用途 |
|--------|------|------|
| MySQL | 8.0 | 主数据库，存储核心业务数据 |
| Redis | 7 | 缓存、分布式锁、WebSocket 多实例广播、会话管理 |
| RocketMQ | 5.3.0 | 异步消息：订单超时取消、支付回调、秒杀下单、聊天分发 |
| Elasticsearch | 8.13.4 | 商品全文搜索（ik 中文分词、高亮、聚合） |
| Nacos | 2.3.2 | 配置中心 + 服务注册与发现（3 节点集群） |
| Zipkin | 2.24 | 分布式链路追踪 |
| Docker | - | 容器化部署，Compose 编排 |

## 三、数据访问层

| 技术 | 版本 | 说明 |
|------|------|------|
| MyBatis-Plus | 3.5.10.1 | ORM 框架，简化 CRUD，支持动态数据源 |
| Flyway | - | 数据库版本管理与自动迁移 |
| Guava Bloom Filter | 32.1.3 | 布隆过滤器，防止缓存穿透（定时重建 + Redis 同步） |

## 四、安全与认证

| 技术 | 说明 |
|------|------|
| Spring Security | 安全框架，拦截器链式认证授权 |
| JWT (jjwt) | 无状态 Token 认证，双 Token（access + refresh） |
| XSS 过滤 | 自定义 XssFilter，过滤请求中的 XSS 攻击脚本 |
| CORS 配置 | 跨域资源共享，支持多域名白名单 |
| 阿里云 OSS | 文件上传（头像、商品图片、茶友圈图片） |

## 五、缓存架构（L1 + L2 + L3 三级缓存）

| 层级 | 技术 | 说明 |
|------|------|------|
| L1 本地缓存 | Caffeine | 进程内高速缓存，60s 过期，最大 2048 条 |
| L2 分布式缓存 | Redis (Lettuce) | 多实例共享，L1 未命中时降级查询 |
| L3 浏览器缓存 | HTTP Cache-Control / ETag | 按 API 路径分级缓存策略（静态资源 1 年，动态接口按需） |
| 缓存防穿透 | Guava Bloom Filter | 大数据量场景下快速判断 key 是否存在，减少无效 DB 查询 |

## 六、业务模块

| 模块 | 说明 |
|------|------|
| 用户 (user) | 注册、登录、JWT 认证、角色权限（买家/商家/管理员）、地址管理 |
| 商品 (product) | SPU/SKU 管理、分类、品牌、产地、标签、商品详情、ES 全文搜索 |
| 店铺 (shop) | 商家入驻、店铺管理、店铺关注 |
| 购物车 (cart) | 添加/删除/修改数量、全选/反选、价格实时计算 |
| 订单 (order) | 创建订单、库存扣减、超时自动取消、确认收货、退款、评价 |
| 支付 (payment) | 支付宝支付集成（含模拟支付）、支付回调、退款 |
| 秒杀 (flashsale) | 高并发秒杀：验证码预热、令牌限流、Redis Lua 原子扣减、MQ 异步下单、定时对账 |
| 茶友圈 (teacircle) | 社区动态、评论、点赞、关注、话题、活动、文件上传 |
| 即时通讯 (chat) | WebSocket 双向通信、Redis Pub/Sub 多实例广播、消息分发 |
| Banner 管理 | 首页轮播图管理 |
| 收藏 (favorite) | 商品收藏 |
| 反馈 (feedback) | 用户反馈提交 |
| 文件上传 (upload) | 阿里云 OSS 上传、图片格式校验 |

## 七、高并发与性能优化

| 技术/方案 | 说明 |
|-----------|------|
| Redis Lua 脚本 | 秒杀库存原子扣减，保证并发安全 |
| 分布式锁 (Redisson) | 订单防重复提交、库存扣减互斥 |
| 令牌桶限流 | Redis + Lua 实现，按 IP 限流（50 QPS，突发 100） |
| 布隆过滤器 | Guava Bloom Filter + Redis 同步，防缓存穿透 |
| 本地缓存 | Caffeine L1 + Redis L2 双级缓存 |
| Resilience4j 熔断 | Redis 故障时自动降级，半开状态恢复 |
| RocketMQ 异步解耦 | 订单超时取消、支付通知、秒杀下单异步处理 |
| Tomcat 线程池调优 | max=400, min-spare=50, accept-count=100 |
| 优雅停机 | Spring Graceful Shutdown，等待请求处理完毕 |

## 八、监控与可观测性

| 技术 | 说明 |
|------|------|
| Spring Boot Actuator | 健康检查端点（health） |
| Prometheus + Micrometer | 指标采集与导出 |
| Zipkin + Brave | 分布式链路追踪，Span 采样率 100% |

## 九、API 文档

| 技术 | 说明 |
|------|------|
| Knife4j 4.5.0 | OpenAPI 3.0 增强文档，中文界面 |
| Swagger 注解 | @Operation、@ApiResponse、@Schema 等注解驱动文档生成 |

## 十、开发工具与规范

| 技术/工具 | 说明 |
|-----------|------|
| Lombok | 减少样板代码（@Data、@Builder、@Slf4j） |
| MapStruct | 类型安全的对象映射 |
| Spring DevTools | 热重载开发 |
| Flyway | 数据库 Schema 版本控制 |
| H2 Database | 单元测试内存数据库 |

## 十一、项目架构亮点

- **模块化分层架构**：Controller → Service → Mapper，模块间低耦合
- **动态数据源**：支持读写分离（DynamicDataSourceConfig）
- **三级缓存体系**：Caffeine → Redis → 浏览器缓存，逐层降级
- **XSS 防护**：自定义 Filter 过滤 JSON/参数中的恶意脚本
- **限流降级**：全局令牌桶限流 + Resilience4j 熔断器
- **秒杀架构**：验证码预热 → Lua 原子扣减 → MQ 异步下单 → 定时对账
- **WebSocket 多实例**：Redis Pub/Sub 实现跨实例消息广播
- **容器化部署**：Docker Compose 编排，支持 dev/prod 环境切换
