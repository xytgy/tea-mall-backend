# Task1 输出：运行方式 / 关键入口 / 典型链路入口文件清单（草稿）

> 目标：让读者在不通读全仓库的前提下，先知道“怎么跑起来、入口在哪里、三条典型链路从哪读起”。

## 1) 运行方式（推荐以配置为准）

### 1.1 本地启动（最小闭环）

- **前置依赖**
  - MySQL 8+（Flyway 启动时自动执行迁移脚本）
  - Redis（登录态、缓存、限流/秒杀等）
  - RocketMQ（秒杀异步下单、订单超时取消、支付通知；本地可选，见 1.3）
- **启动命令**
  - Maven Wrapper 启动：
    - `./mvnw spring-boot:run`
  - IDEA 直接运行启动类：
    - [TeaMallBackendApplication.java](../src/main/java/com/xytgy/teamallbackend/TeaMallBackendApplication.java)
- **默认端口**
  - 以配置为准：`server.port` 默认 `8082`（可用环境变量 `SERVER_PORT` 覆盖）
  - 配置位置：[application.yaml](../src/main/resources/application.yaml)

### 1.2 Profile 与配置分层

- **Profile 选择**
  - 默认 `dev`：`spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}`
  - 配置位置：[application.yaml](../src/main/resources/application.yaml)
- **dev/prod 差异**
  - 开发环境： [application-dev.yaml](../src/main/resources/application-dev.yaml)
    - 通过 `spring.autoconfigure.exclude` 禁用 RocketMQ/Nacos/Elasticsearch 相关 AutoConfiguration（本地更轻量）
    - `knife4j.enable: true`（默认开启）
    - `jwt.secret` 在 dev 给了 fallback（但生产必须用环境变量注入）
  - 生产环境： [application-prod.yaml](../src/main/resources/application-prod.yaml)
    - 默认关闭文档与 Knife4j：`docs.enabled=false`、`knife4j.enable=false`
    - 支付宝配置从环境变量注入：`ALIPAY_*`

### 1.3 关键依赖开关（“能跑起来”优先级）

- **JWT（必须）**
  - 配置：`jwt.secret: ${JWT_SECRET}`（生产必须注入）
  - 入口类：[JwtUtils.java](../src/main/java/com/xytgy/teamallbackend/utils/JwtUtils.java)
  - 相关配置：[application.yaml#jwt](../src/main/resources/application.yaml)
- **API 文档（可选）**
  - 访问路径：`/doc.html`（Knife4j）
  - 开关：
    - `docs.enabled`（控制 Security 白名单是否放行文档路径）
    - `knife4j.enable`（控制 Knife4j 是否启用）
  - 入口配置：[SecurityConfig.java](../src/main/java/com/xytgy/teamallbackend/config/security/SecurityConfig.java)
  - 配置项：[application.yaml#docs/knife4j](../src/main/resources/application.yaml)
- **模拟支付（可选）**
  - 开关：`mock-pay.enabled`（默认 false）
  - 入口： [OrderController.java](../src/main/java/com/xytgy/teamallbackend/module/order/controller/OrderController.java) 的 `/api/order/pay`
  - 配置项：[application.yaml#mock-pay](../src/main/resources/application.yaml)
- **支付宝支付（可选）**
  - 开关：`alipay.enabled`
  - 入口： [PaymentController.java](../src/main/java/com/xytgy/teamallbackend/module/order/controller/PaymentController.java)
  - 配置项：[application-prod.yaml#alipay](../src/main/resources/application-prod.yaml)
- **RocketMQ（按需）**
  - NameServer：`rocketmq.name-server: ${ROCKETMQ_NAMESRV:localhost:9876}`
  - 注意：`dev` profile 默认通过 `spring.autoconfigure.exclude` 禁用 RocketMQ AutoConfiguration，因此本地如果要验证 MQ 链路，需要调整 profile/配置策略。
  - 参考部署：[docker-compose.yml](../docker-compose.yml)

## 2) 关键入口（启动/请求）

### 2.1 应用启动入口

- **Spring Boot 启动类**
  - [TeaMallBackendApplication.java](../src/main/java/com/xytgy/teamallbackend/TeaMallBackendApplication.java)
  - 关键注解：
    - `@SpringBootApplication`
    - `@MapperScan({"com.xytgy.teamallbackend.module.*.mapper", "com.xytgy.teamallbackend.mq.mapper"})`
    - `@EnableAsync`、`@EnableScheduling`

### 2.2 Web 请求入口（MVC + 静态资源）

- **MVC/静态资源/SPA 回退**
  - [WebConfig.java](../src/main/java/com/xytgy/teamallbackend/config/WebConfig.java)
  - 关注点：
    - Knife4j 静态资源映射：`doc.html`、`/webjars/**`
    - `/**` 的 SPA 回退逻辑（避免前端路由 404）

### 2.3 认证与安全入口（Spring Security FilterChain）

- **Security 总入口**
  - [SecurityConfig.java](../src/main/java/com/xytgy/teamallbackend/config/security/SecurityConfig.java)
  - 核心要点：
    - `STATELESS`（不使用 Session，全靠 JWT）
    - 白名单路径：`SecurityConstants.PUBLIC_PATHS`
    - JWT 过滤器插入点：`addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)`
- **公开路径白名单**
  - [SecurityConstants.java](../src/main/java/com/xytgy/teamallbackend/security/SecurityConstants.java)
- **JWT 过滤器（请求进来的第一道业务相关关卡）**
  - [JwtAuthenticationFilter.java](../src/main/java/com/xytgy/teamallbackend/config/security/JwtAuthenticationFilter.java)
  - 关键行为：
    - 白名单路径直接跳过过滤（`shouldNotFilter`）
    - Bearer Token 解析 + Redis 在线态校验 + 用户信息缓存（RedisUtils 的 L1/L2）

## 3) 3 条典型链路：入口文件清单（从“入口”到“关键跳点”）

> 说明：这里只列“看链路最先该点进去的文件”，后续 Task2/Task3 再补阅读路线与断点细化。

### 3.1 登录鉴权链路（JWT + Spring Security）

- **HTTP 入口（Controller）**
  - [AuthController.java](../src/main/java/com/xytgy/teamallbackend/module/user/controller/AuthController.java)（`POST /api/auth/login`）
- **核心业务（Service）**
  - [UserService.java](../src/main/java/com/xytgy/teamallbackend/module/user/service/UserService.java)
  - [UserServiceImpl.java](../src/main/java/com/xytgy/teamallbackend/module/user/service/impl/UserServiceImpl.java)
- **Token 生成/解析**
  - [JwtUtils.java](../src/main/java/com/xytgy/teamallbackend/utils/JwtUtils.java)
- **登录风控/限流**
  - [RateLimitService.java](../src/main/java/com/xytgy/teamallbackend/ratelimit/RateLimitService.java)
- **请求鉴权入口（FilterChain）**
  - [SecurityConfig.java](../src/main/java/com/xytgy/teamallbackend/config/security/SecurityConfig.java)
  - [JwtAuthenticationFilter.java](../src/main/java/com/xytgy/teamallbackend/config/security/JwtAuthenticationFilter.java)
  - [SecurityConstants.java](../src/main/java/com/xytgy/teamallbackend/security/SecurityConstants.java)

### 3.2 下单支付链路（创建订单 → 支付 → 回调/通知）

- **HTTP 入口（Controller）**
  - [OrderController.java](../src/main/java/com/xytgy/teamallbackend/module/order/controller/OrderController.java)
    - `POST /api/order/create`（创建订单）
    - `POST /api/order/pay`（模拟支付；受 `mock-pay.enabled` 控制）
  - [PaymentController.java](../src/main/java/com/xytgy/teamallbackend/module/order/controller/PaymentController.java)（支付宝支付；受 `alipay.enabled` 控制）
    - `/api/payment/alipay/pay`（发起支付）
    - `/api/payment/alipay/notify`（支付宝异步回调）
- **核心业务（Service）**
  - [OrdersService.java](../src/main/java/com/xytgy/teamallbackend/module/order/service/OrdersService.java)
  - [OrdersServiceImpl.java](../src/main/java/com/xytgy/teamallbackend/module/order/service/impl/OrdersServiceImpl.java)
    - 下单落库 + 扣库存 + 发送“订单超时取消”延迟消息
- **MQ 入口（异步链路关键点）**
  - [OrderTimeoutConsumer.java](../src/main/java/com/xytgy/teamallbackend/mq/consumer/OrderTimeoutConsumer.java)（订单超时取消）
  - [PaymentNotifyConsumer.java](../src/main/java/com/xytgy/teamallbackend/mq/consumer/PaymentNotifyConsumer.java)（支付成功通知扩展点）
- **配置与开关（影响链路是否生效）**
  - [application.yaml#mock-pay/rocketmq](../src/main/resources/application.yaml)
  - [application-prod.yaml#alipay](../src/main/resources/application-prod.yaml)

### 3.3 秒杀链路（验证码 → 限流 → Redis Lua 扣减 → MQ 异步下单）

- **HTTP 入口（Controller）**
  - [FlashSaleController.java](../src/main/java/com/xytgy/teamallbackend/module/flashsale/controller/FlashSaleController.java)
    - `GET /api/flash-sale/captcha`（获取验证码）
    - `POST /api/flash-sale/captcha/verify`（验证码换秒杀 token）
    - `POST /api/flash-sale/buy`（抢购）
- **门面/核心业务（Service）**
  - [FlashSaleService.java](../src/main/java/com/xytgy/teamallbackend/module/flashsale/service/FlashSaleService.java)
  - [FlashSaleServiceImpl.java](../src/main/java/com/xytgy/teamallbackend/module/flashsale/service/FlashSaleServiceImpl.java)（门面委托）
  - [FlashSaleCoreService.java](../src/main/java/com/xytgy/teamallbackend/module/flashsale/service/FlashSaleCoreService.java)（核心抢购链路）
- **Lua 原子扣减脚本**
  - [flash_deduct.lua](../src/main/resources/lua/flash_deduct.lua)
  - （扩展）[flash_refund.lua](../src/main/resources/lua/flash_refund.lua)
- **MQ 入口（异步订单创建）**
  - [FlashOrderConsumer.java](../src/main/java/com/xytgy/teamallbackend/mq/consumer/FlashOrderConsumer.java)
- **限流脚本（秒杀限流底座之一）**
  - [sliding_window_limit.lua](../src/main/resources/lua/sliding_window_limit.lua)
- **配置与开关**
  - [application.yaml#flash-sale/rocketmq](../src/main/resources/application.yaml)

