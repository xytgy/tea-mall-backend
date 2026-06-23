# 快速上手导览

> 本文档帮助新成员在 **30 分钟内** 建立对 tea-mall-backend 项目的全局认知，并掌握核心请求链路的调试方法。

---

## 目录

- [一、推荐阅读顺序](#一推荐阅读顺序)
- [二、关键文件索引](#二关键文件索引)
- [三、典型请求链路与断点指南](#三典型请求链路与断点指南)

---

## 一、推荐阅读顺序

遵循 **先宏观后微观** 的原则，按以下 6 步阅读，每一步都有明确的目标。

### 第 1 步：项目全貌 — README.md

| 项目 | 内容 |
|------|------|
| **读什么** | [README.md](../README.md) |
| **为什么读** | 快速了解项目定位（茶商城电商 + 社交平台）、12 个业务模块清单、技术栈选型、目录结构全景图 |
| **读完能得到什么** | 知道项目"做什么"、"用了什么技术"、"代码在哪里"；形成一份心理地图，后续阅读时不至于迷失方向 |

### 第 2 步：依赖管理 — pom.xml

| 项目 | 内容 |
|------|------|
| **读什么** | [pom.xml](../pom.xml) |
| **为什么读** | 了解项目引入了哪些第三方库、版本号、Spring Boot Parent 版本、annotation processor 配置（Lombok + MapStruct） |
| **读完能得到什么** | 明确 Spring Boot 3.3.4 + Java 17 基座；知道安全（Spring Security + JWT）、持久层（MyBatis-Plus）、缓存（Redis + Caffeine + Redisson）、消息队列（RocketMQ）、搜索（Elasticsearch）、监控（Actuator + Prometheus + Zipkin）等关键依赖的版本；理解编译时 MapStruct 代码生成机制 |

### 第 3 步：配置层 — application.yaml 系列

| 项目 | 内容 |
|------|------|
| **读什么** | [application.yaml](../src/main/resources/application.yaml) → [application-dev.yaml](../src/main/resources/application-dev.yaml) → [application-prod.yaml](../src/main/resources/application-prod.yaml) → [bootstrap.yaml](../src/main/resources/bootstrap.yaml) |
| **为什么读** | 主配置文件定义了所有可调参数的默认值和环境变量占位符；dev/prod profile 分别覆盖开发和生产环境差异；bootstrap 负责 Nacos 配置中心的连接 |
| **读完能得到什么** | 掌握配置分层策略（环境变量注入 → profile 覆盖 → Nacos 动态下发）；知道数据库连接池、Redis、JWT 密钥、RocketMQ、秒杀参数、限流参数、浏览器缓存规则等关键配置项的位置和含义；理解 dev 环境会自动排除 RocketMQ/Nacos/ES 的自动装配 |

### 第 4 步：启动入口 — TeaMallBackendApplication.java

| 项目 | 内容 |
|------|------|
| **读什么** | [TeaMallBackendApplication.java](../src/main/java/com/xytgy/teamallbackend/TeaMallBackendApplication.java) |
| **为什么读** | 这是整个应用的启动入口，`@MapperScan` 指定了 MyBatis Mapper 的扫描路径，`@EnableAsync` 和 `@EnableScheduling` 分别启用了异步方法和定时任务 |
| **读完能得到什么** | 理解 Spring Boot 自动装配的起点；知道 Mapper 接口的扫描范围覆盖了 `module.*.mapper` 和 `mq.mapper` 两个包；确认异步和调度能力已全局启用 |

### 第 5 步：安全链路 — SecurityConfig + JwtAuthenticationFilter

| 项目 | 内容 |
|------|------|
| **读什么** | [SecurityConfig.java](../src/main/java/com/xytgy/teamallbackend/config/security/SecurityConfig.java) → [JwtAuthenticationFilter.java](../src/main/java/com/xytgy/teamallbackend/config/security/JwtAuthenticationFilter.java) → [SecurityConstants.java](../src/main/java/com/xytgy/teamallbackend/security/SecurityConstants.java) |
| **为什么读** | 这三个文件构成了请求进入 Controller 之前的完整安全校验链路：SecurityConfig 定义哪些路径公开/受保护、JWT 过滤器在 UsernamePasswordAuthenticationFilter 之前执行 Token 解析、SecurityConstants 维护白名单路径 |
| **读完能得到什么** | 掌握请求从 HTTP 进入到最终到达 Controller 的完整过滤链；理解 STATELESS Session 策略（无 HttpSession，完全依赖 JWT）；知道公开路径白名单的匹配逻辑；理解过滤器如何从 Token 中提取 userId 并构建 `JwtAuthenticationToken` 写入 `SecurityContextHolder` |

### 第 6 步：按需深入业务模块

| 项目 | 内容 |
|------|------|
| **读什么** | `src/main/java/com/xytgy/teamallbackend/module/` 下的各子目录，每个模块遵循统一的分层结构：`controller/` → `service/` → `mapper/` → `entity/`，以及 `dto/`（入参）和 `vo/`（出参） |
| **为什么读** | 业务模块是项目的核心价值所在，理解单个模块的分层结构后，其他模块可举一反三 |
| **读完能得到什么** | 掌握单个业务模块的标准开发范式：Controller 接收请求并做参数校验 → Service 处理业务逻辑和事务 → Mapper 操作数据库 → DTO/VO 隔离入参出参；为后续阅读典型请求链路（第三章）打下基础 |

**12 个业务模块速查表**：

| 模块 | 目录 | 核心职责 |
|------|------|----------|
| 用户与认证 | [user/](../src/main/java/com/xytgy/teamallbackend/module/user/) | 注册、登录、JWT 鉴权、角色管理、收货地址 |
| 商品与分类 | [product/](../src/main/java/com/xytgy/teamallbackend/module/product/) | 商品浏览、评价、ES 搜索、商家商品管理 |
| 店铺 | [shop/](../src/main/java/com/xytgy/teamallbackend/module/shop/) | 商家入驻、店铺信息、店铺关注 |
| 购物车 | [cart/](../src/main/java/com/xytgy/teamallbackend/module/cart/) | 加购、改量、移除 |
| 订单与支付 | [order/](../src/main/java/com/xytgy/teamallbackend/module/order/) | 下单、支付（支付宝/模拟）、退款、物流、商家发货 |
| 秒杀 | [flashsale/](../src/main/java/com/xytgy/teamallbackend/module/flashsale/) | 验证码、限流、Redis Lua 扣减、MQ 异步下单、对账 |
| 茶友圈 | [teacircle/](../src/main/java/com/xytgy/teamallbackend/module/teacircle/) | 动态、评论、点赞、关注、通知、话题、活动 |
| 即时聊天 | [chat/](../src/main/java/com/xytgy/teamallbackend/module/chat/) | WebSocket 实时聊天、未读消息 |
| 收藏夹 | [favorite/](../src/main/java/com/xytgy/teamallbackend/module/favorite/) | 商品收藏 |
| 意见反馈 | [feedback/](../src/main/java/com/xytgy/teamallbackend/module/feedback/) | 用户/游客反馈 |
| 客服工单 | [support/](../src/main/java/com/xytgy/teamallbackend/module/support/) | 售后/咨询工单 |
| 文件上传 | [upload/](../src/main/java/com/xytgy/teamallbackend/module/upload/) | 阿里云 OSS 上传 |

---

## 二、关键文件索引

### 基础设施

| 文件 | 路径（相对于 docs/） | 说明 |
|------|----------------------|------|
| README | [README.md](../README.md) | 项目全貌、技术栈、启动指南 |
| Maven POM | [pom.xml](../pom.xml) | 依赖管理、编译插件配置 |
| 主配置 | [application.yaml](../src/main/resources/application.yaml) | 全量默认配置、环境变量占位符 |
| 开发配置 | [application-dev.yaml](../src/main/resources/application-dev.yaml) | 开发环境覆盖（排除 MQ/Nacos/ES） |
| 生产配置 | [application-prod.yaml](../src/main/resources/application-prod.yaml) | 生产环境覆盖（支付宝、CORS） |
| Bootstrap | [bootstrap.yaml](../src/main/resources/bootstrap.yaml) | Nacos 配置中心连接 |
| 启动类 | [TeaMallBackendApplication.java](../src/main/java/com/xytgy/teamallbackend/TeaMallBackendApplication.java) | 应用入口、MapperScan、EnableAsync |

### 安全与配置

| 文件 | 路径（相对于 docs/） | 说明 |
|------|----------------------|------|
| Security 配置 | [SecurityConfig.java](../src/main/java/com/xytgy/teamallbackend/config/security/SecurityConfig.java) | FilterChain、CORS、白名单、安全头 |
| JWT 过滤器 | [JwtAuthenticationFilter.java](../src/main/java/com/xytgy/teamallbackend/config/security/JwtAuthenticationFilter.java) | Token 解析、在线态校验、SecurityContext 构建 |
| 安全常量 | [SecurityConstants.java](../src/main/java/com/xytgy/teamallbackend/security/SecurityConstants.java) | 公开路径白名单、文档路径白名单 |
| Web 配置 | [WebConfig.java](../src/main/java/com/xytgy/teamallbackend/config/WebConfig.java) | 静态资源映射、SPA 路由回退 |
| 全局异常处理 | [GlobalExceptionHandler.java](../src/main/java/com/xytgy/teamallbackend/exception/GlobalExceptionHandler.java) | 统一异常→JSON 响应转换 |
| JWT 工具 | [JwtUtils.java](../src/main/java/com/xytgy/teamallbackend/utils/JwtUtils.java) | Token 生成与解析 |
| MQ 常量 | [MqConstants.java](../src/main/java/com/xytgy/teamallbackend/mq/constant/MqConstants.java) | Topic/Tag/DelayLevel 定义 |
| Lua 扣减脚本 | [flash_deduct.lua](../src/main/resources/lua/flash_deduct.lua) | 秒杀 Redis 原子扣减逻辑 |

### 配置属性类

| 文件 | 路径（相对于 docs/） | 说明 |
|------|----------------------|------|
| JWT 属性 | [JwtProperties.java](../src/main/java/com/xytgy/teamallbackend/properties/JwtProperties.java) | JWT 密钥、过期时间绑定 |
| CORS 属性 | [CorsProperties.java](../src/main/java/com/xytgy/teamallbackend/properties/CorsProperties.java) | 跨域允许来源 |
| 秒杀属性 | [FlashSaleProperties.java](../src/main/java/com/xytgy/teamallbackend/properties/FlashSaleProperties.java) | 验证码池、Token 过期、限流阈值 |
| 限流属性 | [RateLimitProperties.java](../src/main/java/com/xytgy/teamallbackend/properties/RateLimitProperties.java) | 全局 API 限流参数 |

---

## 三、典型请求链路与断点指南

> 每条链路均标注了 **关键断点位置**（过滤器、事务边界、MQ 发送/消费、Redis 操作），
> 方便在 IDE 中设置断点进行单步调试。

### 链路 1：登录鉴权（JWT + Spring Security Filter）

**场景**：用户输入账号密码 → 获取 Token → 后续请求携带 Token 访问受保护接口

#### 1.1 登录流程

```
客户端 POST /api/auth/login
  │
  ├─ [断点 1] GlobalRateLimitFilter.doFilter()          ← 全局 IP 限流
  ├─ [断点 2] SecurityConfig.filterChain()              ← 路由匹配，/api/auth/login 属于白名单，直接放行
  │
  ├─ [断点 3] AuthController.login()                    ← 入口：IP 维度限速检查 → 调用 UserService.login()
  │
  ├─ [断点 4] UserServiceImpl.login()                   ← 核心逻辑
  │     ├─ RateLimitService.isAccountLocked()           ← 账号维度限速防暴力破解
  │     ├─ QueryWrapper 查询 DB 获取 User
  │     ├─ PasswordUtil.match()                         ← BCrypt 密码校验
  │     ├─ rateLimitService.resetAccountAttempts()      ← 登录成功，重置失败计数
  │     └─ createLoginResponse()                        ← 生成 Token
  │           ├─ [断点 5] JwtUtils.createAccessToken()  ← JWT 签发（HMAC-SHA256）
  │           ├─ Redis: SET login:user:{id} = "online"  ← 写入在线状态（TTL 7天）
  │           ├─ Redis: SET login:refresh:token:{uuid}  ← 写入 RefreshToken（TTL 7天）
  │           ├─ Redis: SADD login:user:refresh:{id}    ← 维护用户→RefreshToken 反向映射
  │           └─ Redis: SET user:info:{id} = UserInfoCache ← 写入用户信息缓存
  │
  └─ 返回 { accessToken, refreshToken, userInfo }
```

#### 1.2 后续请求鉴权流程

```
客户端 GET /api/order/list  (Authorization: Bearer <token>)
  │
  ├─ [断点 6] SecurityConfig.filterChain()              ← 匹配 anyRequest().authenticated()
  │
  ├─ [断点 7] JwtAuthenticationFilter.doFilterInternal() ← 核心鉴权
  │     ├─ shouldNotFilter() → SecurityConstants.isPublicPath()  ← 白名单路径直接跳过
  │     ├─ 提取 Authorization 头 → 去掉 "Bearer " 前缀
  │     ├─ [断点 8] JwtUtils.parseToken()               ← 解析 JWT，提取 claims（id, userAccount, role）
  │     ├─ Redis: EXISTS login:user:{id}                ← 校验在线状态，不存在则返回 401
  │     ├─ [断点 9] loadUserInfo()                      ← L1 Caffeine + L2 Redis 两级缓存读取用户信息
  │     │     └─ 缓存未命中 → loadUserInfoFromDb()      ← 降级查 DB 并回填缓存
  │     ├─ 构建 JwtAuthenticationToken(userId, role, shopId, authorities)
  │     └─ SecurityContextHolder.getContext().setAuthentication()  ← 写入安全上下文
  │
  └─ 请求继续到达 Controller → Service → Mapper
```

#### 1.3 关键断点清单

| 断点 | 文件 | 方法/行 | 观察目标 |
|------|------|---------|----------|
| #3 | AuthController.java | `login()` | 请求参数、IP 限流结果 |
| #4 | UserServiceImpl.java | `login()` | DB 查询结果、密码校验结果 |
| #5 | JwtUtils.java | `createAccessToken()` | claims 内容、签名密钥 |
| #7 | JwtAuthenticationFilter.java | `doFilterInternal()` | Token 提取、解析结果 |
| #8 | JwtUtils.java | `parseToken()` | Token 签名验证、claims 解析 |
| #9 | JwtAuthenticationFilter.java | `loadUserInfo()` | 缓存命中/未命中、DB 降级路径 |

---

### 链路 2：下单支付（创建订单 → 支付 → 回调/超时）

**场景**：用户从购物车下单 → 发起支付 → 支付宝回调确认（或 30 分钟超时自动取消）

#### 2.1 创建订单

```
客户端 POST /api/order/create
  │
  ├─ JwtAuthenticationFilter 鉴权（同链路 1.2）
  │
  ├─ [断点 1] OrderController.create()                  ← 入口，获取当前 userId
  │
  ├─ [断点 2] OrdersServiceImpl.createOrder()           ← @Transactional 事务边界
  │     ├─ buildAndValidateProductQtyMap()               ← 校验请求参数
  │     ├─ loadAndValidateProducts()                     ← 查询商品是否存在
  │     ├─ validateStockAndCalcTotal()                   ← 校验库存、计算总金额
  │     ├─ Orders.save()                                 ← [事务内] 订单落库
  │     ├─ [断点 3] deductStockAndSaveItems()            ← [事务内] 乐观锁扣减库存
  │     │     └─ SQL: UPDATE product SET stock = stock - ? WHERE id = ? AND stock >= ?
  │     ├─ cartService.removeByUserAndProductIds()       ← [事务内] 清除已购商品的购物车记录
  │     └─ [断点 4] MqProducer.sendDelay()              ← [事务提交后] 发送延迟消息
  │           ├─ Topic: TOPIC_ORDER_TIMEOUT
  │           ├─ Tag: TIMEOUT_CANCEL
  │           └─ DelayLevel: 6（30 分钟）
  │
  └─ 返回 { orderNo, orderId }
```

#### 2.2 发起支付（支付宝电脑网站支付）

```
客户端 GET/POST /api/payment/alipay/pay?orderId=xxx
  │
  ├─ [断点 5] PaymentController.pay()                   ← 入口（仅 alipay.enabled=true 时可用）
  │     ├─ 校验订单归属和状态
  │     ├─ 创建 PaymentRecord（status=PAYING）
  │     ├─ 构建 AlipayTradePagePayRequest
  │     └─ [断点 6] alipayClient.pageExecute()          ← 调用支付宝 API，返回支付页面 HTML
  │
  └─ 客户端跳转支付宝收银台完成支付
```

#### 2.3 支付回调（异步通知）

```
支付宝服务器 POST /api/payment/alipay/notify
  │
  ├─ [断点 7] PaymentController.notifyCallback()        ← @Transactional 事务边界
  │     ├─ [断点 8] verifyNotifyParams()                ← RSA 签名验证 + appId 校验
  │     ├─ Redis: SETNX payment:idempotent:{outTradeNo} ← 幂等性保护，防止重复回调
  │     ├─ 校验金额一致性
  │     ├─ [断点 9] DistributedLock.tryLock()            ← 分布式锁防并发
  │     └─ confirmPayment()
  │           ├─ 更新 PaymentRecord.status = PAID
  │           ├─ 更新 Orders.status = PAID
  │           └─ [断点 10] MqProducer.send()             ← 发送支付成功通知消息
  │                 ├─ Topic: TOPIC_PAYMENT_NOTIFY
  │                 └─ Tag: PAY_SUCCESS
  │
  └─ [MQ 消费] PaymentNotifyConsumer.onMessage()        ← 异步处理支付后通知（站内信/短信等）
```

#### 2.4 超时自动取消

```
RocketMQ 延迟消息 30 分钟后投递
  │
  ├─ [断点 11] OrderTimeoutConsumer.onMessage()         ← 消费延迟消息
  │     ├─ 查询订单状态
  │     ├─ 仅处理 status == PENDING_PAYMENT 的订单
  │     └─ [断点 12] OrdersService.doCancelOrderInTransaction()  ← @Transactional(REQUIRES_NEW)
  │           ├─ 恢复库存：UPDATE product SET stock = stock + ?
  │           └─ 更新订单状态：status = CANCELLED
  │
  └─ 订单自动取消完成
```

#### 2.5 关键断点清单

| 断点 | 文件 | 方法/行 | 观察目标 |
|------|------|---------|----------|
| #2 | OrdersServiceImpl.java | `createOrder()` | 事务边界、库存校验逻辑 |
| #3 | OrdersServiceImpl.java | `deductStockAndSaveItems()` | 乐观锁 SQL、扣减结果 |
| #4 | OrdersServiceImpl.java | `createOrder()` 末尾 | MQ 延迟消息发送 |
| #5 | PaymentController.java | `pay()` | 支付宝请求构建 |
| #7 | PaymentController.java | `notifyCallback()` | 回调参数、签名验证 |
| #9 | PaymentController.java | `notifyCallback()` | 分布式锁获取、并发控制 |
| #11 | OrderTimeoutConsumer.java | `onMessage()` | 超时消息消费、订单状态检查 |
| #12 | OrdersServiceImpl.java | `doCancelOrderInTransaction()` | 库存恢复、事务传播行为 |

---

### 链路 3：秒杀（验证码 → 限流 → Redis Lua 扣减 → MQ 异步下单）

**场景**：用户获取验证码 → 验证通过获取秒杀 Token → 发起抢购 → Redis 原子扣减 → MQ 异步创建订单

#### 3.1 获取验证码

```
客户端 GET /api/flash-sale/captcha
  │
  ├─ [断点 1] FlashSaleController.captcha()             ← 入口
  │
  ├─ FlashSaleService.generateCaptcha(userId)
  │     ├─ 生成随机数学运算题（如 3 + 5 = ?）
  │     ├─ 生成验证码图片（Base64）
  │     └─ Redis: 存储验证码答案（TTL 60 秒）
  │
  └─ 返回 { uuid, imageBase64 }
```

#### 3.2 验证码校验 → 获取秒杀 Token

```
客户端 POST /api/flash-sale/captcha/verify
  │
  ├─ [断点 2] FlashSaleController.verifyCaptcha()       ← 入口
  │
  ├─ FlashSaleService.verifyCaptcha(userId, request)
  │     ├─ Redis: GET captcha:{uuid} 比对用户输入的答案
  │     ├─ 验证通过后生成一次性秒杀 Token
  │     └─ Redis: SET captcha_token:{token} = userId (TTL 15 秒)
  │
  └─ 返回秒杀 Token 字符串
```

#### 3.3 发起抢购（核心链路）

```
客户端 POST /api/flash-sale/buy
  │
  ├─ [断点 3] FlashSaleController.buy()                 ← 入口
  │
  ├─ [断点 4] FlashSaleCoreService.buy()                ← @CircuitBreaker(name="redis")
  │     ├─ loadActivity()                               ← 从缓存/DB 加载活动信息
  │     ├─ loadActivityProduct()                        ← 从缓存/DB 加载活动商品信息
  │     ├─ isActivityTimeValid()                        ← 校验活动时间窗口
  │     │
  │     ├─ [断点 5] checkRateLimitAndCaptcha()           ← 限流检查
  │     │     ├─ FlashSaleRateLimiter.check()            ← 滑动窗口限流（Lua 脚本）
  │     │     └─ 恶意用户黑名单机制
  │     │
  │     ├─ [断点 6] submitBehaviorAnalysis()             ← 异步行为分析（独立线程池）
  │     │
  │     ├─ Redis: GETDEL captcha_token:{token}           ← 一次性消费秒杀 Token
  │     │
  │     ├─ [断点 7] validateAndDecrementStock()          ← 核心：Redis Lua 原子扣减
  │     │     ├─ cacheManager.isLocalStockEmpty()        ← L1 本地缓存预检（快速失败）
  │     │     └─ [断点 8] Redis Lua 执行                 ← flash_deduct.lua
  │     │           ├─ KEYS[1] = {flash:productId}:stock
  │     │           ├─ KEYS[2] = {flash:productId}:bought:{flashSaleId}
  │     │           ├─ ARGV[1] = userId
  │     │           ├─ SISMEMBER 检查是否已购买          ← 返回 -1 表示已达限购
  │     │           ├─ GET 检查库存是否 > 0              ← 返回 0 表示售罄
  │     │           ├─ DECR 扣减库存
  │     │           └─ SADD 标记已购买                   ← 返回剩余库存数
  │     │
  │     ├─ handleDeductResult()                         ← 处理扣减结果
  │     │
  │     └─ [断点 9] processDeductSuccess()               ← 扣减成功后处理
  │           ├─ cacheManager.syncLocalStock()           ← 同步本地缓存
  │           ├─ Redis: SET flash:pending:{transactionId} ← 写入待处理标记（TTL 30 分钟）
  │           └─ [断点 10] MqProducer.send()             ← 发送异步下单消息
  │                 ├─ Topic: TOPIC_FLASH_ORDER
  │                 └─ Tag: FLASH_ORDER
  │
  └─ 返回 { status: "SUCCESS", message: "抢购成功，订单生成中" }
```

#### 3.4 MQ 异步创建订单

```
RocketMQ 投递秒杀下单消息
  │
  ├─ [断点 11] FlashOrderConsumer.onMessage()            ← 消息入缓冲队列（BlockingQueue）
  │
  ├─ [断点 12] FlashOrderConsumer.processBatch()         ← 多线程批量消费（默认 4 线程）
  │     └─ processSingleMessage()
  │           ├─ 幂等检查：SELECT COUNT(*) WHERE orderNo = transactionId
  │           ├─ OrdersMapper.insert(order)               ← 订单落库
  │           ├─ ProductMapper.update() WHERE stock > 0   ← 乐观锁扣减 DB 库存
  │           ├─ Redis: DEL flash:pending:{transactionId}  ← 清除待处理标记
  │           └─ 失败时 → retryInsert() → 再失败 → writeFailedOrder()  ← 写入死信表
  │
  └─ 客户端轮询 GET /api/flash-sale/result/{orderId}     ← 查询订单生成结果
```

#### 3.5 Redis 不可用时的降级路径

```
FlashSaleCoreService.buy()
  │
  ├─ [断点 13] @CircuitBreaker 熔断器触发               ← buyFallback() 返回友好提示
  │
  └─ Redis Lua 执行异常 → 返回 DEDUCT_DEGRADE_SIGNAL
        │
        └─ [断点 14] degradeToMySql()                    ← 降级到 MySQL 乐观锁
              ├─ AtomicInteger 限流（每秒最多 100 次）
              ├─ UPDATE product SET stock = stock - 1 WHERE stock > 0
              └─ 直接创建订单（不走 MQ，避免双重故障）
```

#### 3.6 关键断点清单

| 断点 | 文件 | 方法/行 | 观察目标 |
|------|------|---------|----------|
| #4 | FlashSaleCoreService.java | `buy()` | 入口参数、活动信息加载 |
| #5 | FlashSaleCoreService.java | `checkRateLimitAndCaptcha()` | 限流判定结果 |
| #7 | FlashSaleCoreService.java | `validateAndDecrementStock()` | 本地缓存预检、Lua 脚本参数 |
| #8 | flash_deduct.lua | 整个脚本 | Redis 原子操作：限购检查 → 库存扣减 → 已购标记 |
| #9 | FlashSaleCoreService.java | `processDeductSuccess()` | MQ 消息构建与发送 |
| #11 | FlashOrderConsumer.java | `onMessage()` | 消息入队、背压传导 |
| #12 | FlashOrderConsumer.java | `processBatch()` | 批量消费、幂等检查、DB 落库 |
| #14 | FlashSaleCoreService.java | `degradeToMySql()` | 降级限流计数器、MySQL 乐观锁 |

---

## 附录：快速导航

### 按职责查找文件

| 职责 | 入口文件 |
|------|----------|
| 请求入口 | `module/*/controller/*Controller.java` |
| 业务逻辑 | `module/*/service/impl/*ServiceImpl.java` |
| 数据库操作 | `module/*/mapper/*Mapper.java` + `resources/mapper/**/*.xml` |
| MQ 生产者 | [MqProducer.java](../src/main/java/com/xytgy/teamallbackend/mq/producer/MqProducer.java) |
| MQ 消费者 | `mq/consumer/*Consumer.java` |
| 分布式锁 | [DistributedLock.java](../src/main/java/com/xytgy/teamallbackend/lock/DistributedLock.java) |
| 限流服务 | [RateLimitService.java](../src/main/java/com/xytgy/teamallbackend/ratelimit/RateLimitService.java) |
| Redis 工具 | [RedisUtils.java](../src/main/java/com/xytgy/teamallbackend/utils/RedisUtils.java) |
| 安全工具 | [SecurityUtils.java](../src/main/java/com/xytgy/teamallbackend/security/SecurityUtils.java) |
| 数据库迁移 | `resources/db/migration/V*.sql` |
| Redis Lua 脚本 | `resources/lua/*.lua` |

### 订单状态流转

```
0-待支付 → 1-已支付 → 2-已发货 → 3-已完成
    ↓         ↓
    4-已取消   5-退款申请中 → 6-已退款 / 7-退款被拒
```
