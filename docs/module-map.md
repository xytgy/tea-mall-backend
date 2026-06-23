# 模块地图

> tea-mall-backend · Spring Boot 3 单体 MVC 架构 · 共 12 个业务模块

---

## 目录

1. [user — 用户与认证](#1-user--用户与认证)
2. [product — 商品与店铺相关](#2-product--商品与店铺相关)
3. [shop — 店铺管理](#3-shop--店铺管理)
4. [cart — 购物车](#4-cart--购物车)
5. [order — 订单与支付](#5-order--订单与支付)
6. [flashsale — 秒杀](#6-flashsale--秒杀)
7. [teacircle — 茶友圈社交](#7-teacircle--茶友圈社交)
8. [chat — 即时聊天](#8-chat--即时聊天)
9. [favorite — 收藏夹](#9-favorite--收藏夹)
10. [feedback — 意见反馈](#10-feedback--意见反馈)
11. [support — 客服工单](#11-support--客服工单)
12. [upload — 文件上传](#12-upload--文件上传)
13. [跨模块基础设施](#跨模块基础设施)
14. [跨组件依赖总览](#跨组件依赖总览)

---

## 1. user — 用户与认证

**目录**: `../src/main/java/com/xytgy/teamallbackend/module/user/`

**主要职责**: 用户注册、登录鉴权、JWT Token 管理、用户信息管理、收货地址管理、管理员用户管理

### Controller

| 类名 | 文件路径 |
|------|---------|
| AuthController | `../src/main/java/com/xytgy/teamallbackend/module/user/controller/AuthController.java` |
| UserController | `../src/main/java/com/xytgy/teamallbackend/module/user/controller/UserController.java` |
| AdminUserController | `../src/main/java/com/xytgy/teamallbackend/module/user/controller/AdminUserController.java` |
| AddressController | `../src/main/java/com/xytgy/teamallbackend/module/user/controller/AddressController.java` |

### Service

| 类名 | 文件路径 |
|------|---------|
| UserService | `../src/main/java/com/xytgy/teamallbackend/module/user/service/UserService.java` |
| UserServiceImpl | `../src/main/java/com/xytgy/teamallbackend/module/user/service/impl/UserServiceImpl.java` |
| AddressService | `../src/main/java/com/xytgy/teamallbackend/module/user/service/AddressService.java` |
| AddressServiceImpl | `../src/main/java/com/xytgy/teamallbackend/module/user/service/impl/AddressServiceImpl.java` |

### Mapper

| 类名 | 文件路径 |
|------|---------|
| UserMapper | `../src/main/java/com/xytgy/teamallbackend/module/user/mapper/UserMapper.java` |
| UserStatsMapper | `../src/main/java/com/xytgy/teamallbackend/module/user/mapper/UserStatsMapper.java` |
| AddressMapper | `../src/main/java/com/xytgy/teamallbackend/module/user/mapper/AddressMapper.java` |

### Entity / DTO / VO

- **Entity**: User, Address
- **DTO**: RegisterRequest, LoginRequest, AdminUserAddRequest, UserProfileUpdateRequest, UserStatusRequest, AddressAddRequest, AddressUpdateRequest
- **VO**: LoginResponse, UserInfoVO, UserVO, UserOverviewStatsVO

### 其他

| 类名 | 说明 | 文件路径 |
|------|------|---------|
| UserInfoCache | 用户信息缓存 DTO（存 Redis，供 JwtAuthenticationFilter 快速校验） | `../src/main/java/com/xytgy/teamallbackend/module/user/cache/UserInfoCache.java` |

### 跨组件依赖

| 组件 | 使用方式 | 说明 |
|------|---------|------|
| **Redis** | `StringRedisTemplate` | 用户信息缓存（UserInfoCache）、JWT Token 黑名单、登录速率限制（通过 RateLimitService） |
| **Flyway** | V1, V3 | V1 创建 user/address 表，V3 种子数据 |

---

## 2. product — 商品与店铺相关

**目录**: `../src/main/java/com/xytgy/teamallbackend/module/product/`

**主要职责**: 商品 CRUD、商品分类管理、商品审核、商品评价、商家商品管理、Elasticsearch 全文搜索与数据同步

### Controller

| 类名 | 文件路径 |
|------|---------|
| ProductController | `../src/main/java/com/xytgy/teamallbackend/module/product/controller/ProductController.java` |
| ProductCategoryController | `../src/main/java/com/xytgy/teamallbackend/module/product/controller/ProductCategoryController.java` |
| ProductSearchController | `../src/main/java/com/xytgy/teamallbackend/module/product/controller/ProductSearchController.java` |
| MerchantGoodsController | `../src/main/java/com/xytgy/teamallbackend/module/product/controller/MerchantGoodsController.java` |
| ProductSyncController | `../src/main/java/com/xytgy/teamallbackend/module/product/controller/admin/ProductSyncController.java` |

### Service

| 类名 | 文件路径 |
|------|---------|
| ProductService | `../src/main/java/com/xytgy/teamallbackend/module/product/service/ProductService.java` |
| ProductServiceImpl | `../src/main/java/com/xytgy/teamallbackend/module/product/service/impl/ProductServiceImpl.java` |
| ProductCategoryService | `../src/main/java/com/xytgy/teamallbackend/module/product/service/ProductCategoryService.java` |
| ProductCategoryServiceImpl | `../src/main/java/com/xytgy/teamallbackend/module/product/service/impl/ProductCategoryServiceImpl.java` |
| ProductSearchService | `../src/main/java/com/xytgy/teamallbackend/module/product/service/ProductSearchService.java` |
| ProductSyncService | `../src/main/java/com/xytgy/teamallbackend/module/product/service/ProductSyncService.java` |

### Mapper

| 类名 | 文件路径 |
|------|---------|
| ProductMapper | `../src/main/java/com/xytgy/teamallbackend/module/product/mapper/ProductMapper.java` |
| ProductCategoryMapper | `../src/main/java/com/xytgy/teamallbackend/module/product/mapper/ProductCategoryMapper.java` |
| ProductReviewMapper | `../src/main/java/com/xytgy/teamallbackend/module/product/mapper/ProductReviewMapper.java` |

### Repository (Elasticsearch)

| 类名 | 文件路径 |
|------|---------|
| ProductRepository | `../src/main/java/com/xytgy/teamallbackend/module/product/repository/ProductRepository.java` |

### Document (Elasticsearch)

| 类名 | 文件路径 |
|------|---------|
| ProductDocument | `../src/main/java/com/xytgy/teamallbackend/module/product/document/ProductDocument.java` |

### Entity / DTO / VO

- **Entity**: Product, ProductCategory, ProductReview
- **DTO**: ProductAddRequest, ProductUpdateRequest, ProductStatusRequest, ProductAuditRequest, MerchantGoodsAddRequest, ProductSearchRequest, ProductSearchResponse
- **VO**: ProductVO, CategoryVO, AuditVO, IdVO, ProductReviewVO

### 跨组件依赖

| 组件 | 使用方式 | 说明 |
|------|---------|------|
| **Elasticsearch** | `ElasticsearchOperations` / `ProductRepository` | 商品全文搜索（ik 分词）、搜索建议、热门搜索词 |
| **Redis** | `RedisUtils` | 搜索结果缓存（5 分钟）、搜索建议缓存（10 分钟）、热门搜索词缓存（30 分钟）、同步状态记录、分布式同步锁 |
| **Flyway** | V1, V3, V4, V6, V9 | V1 创建 product/product_category 表，V3 种子数据，V4/V6/V9 性能索引 |

---

## 3. shop — 店铺管理

**目录**: `../src/main/java/com/xytgy/teamallbackend/module/shop/`

**主要职责**: 商家店铺注册、信息更新、店铺关注/取关、店铺公开展示

### Controller

| 类名 | 文件路径 |
|------|---------|
| ShopController | `../src/main/java/com/xytgy/teamallbackend/module/shop/controller/ShopController.java` |
| ShopFollowController | `../src/main/java/com/xytgy/teamallbackend/module/shop/controller/ShopFollowController.java` |
| PublicStoreController | `../src/main/java/com/xytgy/teamallbackend/module/shop/controller/PublicStoreController.java` |

### Service

| 类名 | 文件路径 |
|------|---------|
| ShopService | `../src/main/java/com/xytgy/teamallbackend/module/shop/service/ShopService.java` |
| ShopServiceImpl | `../src/main/java/com/xytgy/teamallbackend/module/shop/service/impl/ShopServiceImpl.java` |
| ShopFollowService | `../src/main/java/com/xytgy/teamallbackend/module/shop/service/ShopFollowService.java` |
| ShopFollowServiceImpl | `../src/main/java/com/xytgy/teamallbackend/module/shop/service/impl/ShopFollowServiceImpl.java` |

### Mapper

| 类名 | 文件路径 |
|------|---------|
| ShopMapper | `../src/main/java/com/xytgy/teamallbackend/module/shop/mapper/ShopMapper.java` |
| ShopFollowMapper | `../src/main/java/com/xytgy/teamallbackend/module/shop/mapper/ShopFollowMapper.java` |

### Entity / DTO / VO

- **Entity**: Shop, ShopFollow
- **DTO**: ShopRegisterRequest, ShopUpdateRequest
- **VO**: ShopVO

### 跨组件依赖

| 组件 | 使用方式 | 说明 |
|------|---------|------|
| **Flyway** | V1, V10 | V1 创建 shop 表，V10 创建 shop_follow 表 |

---

## 4. cart — 购物车

**目录**: `../src/main/java/com/xytgy/teamallbackend/module/cart/`

**主要职责**: 购物车商品添加、数量修改、删除、列表查询

### Controller

| 类名 | 文件路径 |
|------|---------|
| CartController | `../src/main/java/com/xytgy/teamallbackend/module/cart/controller/CartController.java` |

### Service

| 类名 | 文件路径 |
|------|---------|
| CartService | `../src/main/java/com/xytgy/teamallbackend/module/cart/service/CartService.java` |
| CartServiceImpl | `../src/main/java/com/xytgy/teamallbackend/module/cart/service/impl/CartServiceImpl.java` |

### Mapper

| 类名 | 文件路径 |
|------|---------|
| CartMapper | `../src/main/java/com/xytgy/teamallbackend/module/cart/mapper/CartMapper.java` |

### Entity / DTO / VO

- **Entity**: Cart
- **DTO**: CartAddRequest, CartUpdateRequest, CartDeleteRequest
- **VO**: CartItemVO

### 跨组件依赖

| 组件 | 使用方式 | 说明 |
|------|---------|------|
| **Flyway** | V1, V4, V9 | V1 创建 cart 表，V4/V9 性能索引 |

---

## 5. order — 订单与支付

**目录**: `../src/main/java/com/xytgy/teamallbackend/module/order/`

**主要职责**: 订单创建、支付宝支付/退款/关闭、订单超时取消、订单评价、商家订单管理

### Controller

| 类名 | 文件路径 |
|------|---------|
| OrderController | `../src/main/java/com/xytgy/teamallbackend/module/order/controller/OrderController.java` |
| PaymentController | `../src/main/java/com/xytgy/teamallbackend/module/order/controller/PaymentController.java` |

### Service

| 类名 | 文件路径 |
|------|---------|
| OrdersService | `../src/main/java/com/xytgy/teamallbackend/module/order/service/OrdersService.java` |
| OrdersServiceImpl | `../src/main/java/com/xytgy/teamallbackend/module/order/service/impl/OrdersServiceImpl.java` |
| OrderItemService | `../src/main/java/com/xytgy/teamallbackend/module/order/service/OrderItemService.java` |
| OrderItemServiceImpl | `../src/main/java/com/xytgy/teamallbackend/module/order/service/impl/OrderItemServiceImpl.java` |
| PaymentRecordService | `../src/main/java/com/xytgy/teamallbackend/module/order/service/PaymentRecordService.java` |
| PaymentRecordServiceImpl | `../src/main/java/com/xytgy/teamallbackend/module/order/service/impl/PaymentRecordServiceImpl.java` |

### Mapper

| 类名 | 文件路径 |
|------|---------|
| OrdersMapper | `../src/main/java/com/xytgy/teamallbackend/module/order/mapper/OrdersMapper.java` |
| OrderItemMapper | `../src/main/java/com/xytgy/teamallbackend/module/order/mapper/OrderItemMapper.java` |
| PaymentRecordMapper | `../src/main/java/com/xytgy/teamallbackend/module/order/mapper/PaymentRecordMapper.java` |

### Entity / DTO / VO

- **Entity**: Orders, OrderItem, PaymentRecord
- **DTO**: OrderCreateRequest, OrderPayRequest, OrderRefundRefuseRequest, OrderReviewRequest
- **VO**: OrderVO, OrderItemVO, CreateOrderVO, MerchantOrderVO, OrderStatsVO, LogisticsVO

### 跨组件依赖

| 组件 | 使用方式 | 说明 |
|------|---------|------|
| **RocketMQ** | 生产：`TOPIC_ORDER_TIMEOUT` (TAG: `TIMEOUT_CANCEL`)、`TOPIC_PAYMENT_NOTIFY` (TAG: `PAY_SUCCESS`) | 订单超时延迟消息（30 分钟）、支付成功异步通知 |
| **Redis** | `StringRedisTemplate`（PaymentController） | 支付回调幂等校验 |
| **支付宝 SDK** | `AlipayClient` | 支付宝统一收单交易查询/退款/关闭 |
| **Flyway** | V1, V4, V6, V9 | V1 创建 order 相关表，V4/V6/V9 性能索引 |

---

## 6. flashsale — 秒杀

**目录**: `../src/main/java/com/xytgy/teamallbackend/module/flashsale/`

**主要职责**: 秒杀活动管理、Redis Lua 原子库存扣减、梯度限流与行为分析、验证码防刷、对账与补偿、多级降级

### Controller

| 类名 | 文件路径 |
|------|---------|
| FlashSaleController | `../src/main/java/com/xytgy/teamallbackend/module/flashsale/controller/FlashSaleController.java` |
| FlashSaleAdminController | `../src/main/java/com/xytgy/teamallbackend/module/flashsale/controller/FlashSaleAdminController.java` |

### Service / Component

| 类名 | 说明 | 文件路径 |
|------|------|---------|
| FlashSaleService | 秒杀业务接口 | `../src/main/java/com/xytgy/teamallbackend/module/flashsale/service/FlashSaleService.java` |
| FlashSaleServiceImpl | 秒杀业务实现 | `../src/main/java/com/xytgy/teamallbackend/module/flashsale/service/FlashSaleServiceImpl.java` |
| FlashSaleAdminService | 管理端服务（活动创建/审核/库存管理/白名单/补偿） | `../src/main/java/com/xytgy/teamallbackend/module/flashsale/service/FlashSaleAdminService.java` |
| FlashSaleCoreService | 秒杀核心服务（Lua 扣减/降级/MQ 发送） | `../src/main/java/com/xytgy/teamallbackend/module/flashsale/service/FlashSaleCoreService.java` |
| FlashSaleRateLimiter | Redis 滑动窗口梯度限流（黑/白名单） | `../src/main/java/com/xytgy/teamallbackend/module/flashsale/service/FlashSaleRateLimiter.java` |
| FlashSaleCaptchaService | 验证码生成与验证 | `../src/main/java/com/xytgy/teamallbackend/module/flashsale/service/FlashSaleCaptchaService.java` |
| FlashSaleBehaviorAnalyzer | 异常行为检测（点击频率/同设备多账号） | `../src/main/java/com/xytgy/teamallbackend/module/flashsale/service/FlashSaleBehaviorAnalyzer.java` |
| FlashSaleNotificationService | 失败通知（站内信 + WebSocket 推送） | `../src/main/java/com/xytgy/teamallbackend/module/flashsale/service/FlashSaleNotificationService.java` |
| FlashSaleReconcileTask | 定时对账与自动清理 | `../src/main/java/com/xytgy/teamallbackend/module/flashsale/service/FlashSaleReconcileTask.java` |
| FlashSaleMetrics | 内存计数器指标（LongAdder） | `../src/main/java/com/xytgy/teamallbackend/module/flashsale/service/FlashSaleMetrics.java` |
| CaptchaPool | 预生成验证码池 | `../src/main/java/com/xytgy/teamallbackend/module/flashsale/service/CaptchaPool.java` |

### Mapper

| 类名 | 文件路径 |
|------|---------|
| FlashSaleMapper | `../src/main/java/com/xytgy/teamallbackend/module/flashsale/mapper/FlashSaleMapper.java` |
| FlashSaleProductMapper | `../src/main/java/com/xytgy/teamallbackend/module/flashsale/mapper/FlashSaleProductMapper.java` |
| FlashSaleAuditLogMapper | `../src/main/java/com/xytgy/teamallbackend/module/flashsale/mapper/FlashSaleAuditLogMapper.java` |
| FlashSaleCompensationMapper | `../src/main/java/com/xytgy/teamallbackend/module/flashsale/mapper/FlashSaleCompensationMapper.java` |
| FlashSaleFailedOrderMapper | `../src/main/java/com/xytgy/teamallbackend/module/flashsale/mapper/FlashSaleFailedOrderMapper.java` |
| FlashSaleWhitelistMapper | `../src/main/java/com/xytgy/teamallbackend/module/flashsale/mapper/FlashSaleWhitelistMapper.java` |

### Entity / DTO / VO

- **Entity**: FlashSale, FlashSaleProduct, FlashSaleAuditLog, FlashSaleCompensation, FlashSaleFailedOrder, FlashSaleWhitelist
- **DTO**: FlashSaleBuyRequest, CaptchaVerifyRequest, FlashSaleRestockRequest
- **VO**: FlashSaleVO, FlashSaleProductVO

### 跨组件依赖

| 组件 | 使用方式 | 说明 |
|------|---------|------|
| **Redis** | `StringRedisTemplate`（重度使用） | 库存原子扣减（Lua 脚本 `flash_deduct.lua`）、滑动窗口限流（Lua 脚本 `sliding_window_limit.lua`）、验证码存储、黑名单/白名单、行为分析数据、活动信息缓存（Hash）、Pub/Sub 库存广播 |
| **Redis Lua** | `lua/flash_deduct.lua` | 库存扣减 + 限购检查原子操作 |
| **Redis Lua** | `lua/sliding_window_limit.lua` | 滑动窗口限流原子计数 |
| **Redis Lua** | `lua/flash_refund.lua` | 库存回补 |
| **Redis Pub/Sub** | channel `flash:stock:sync` | 多实例库存同步广播 |
| **RocketMQ** | 生产：`TOPIC_FLASH_ORDER` (TAG: `FLASH_ORDER`) | 秒杀订单异步创建 |
| **WebSocket** | `ChatWebSocketHandler.sendNotificationToUser()` | 秒杀失败实时推送 |
| **Resilience4j** | `@CircuitBreaker(name = "redis")` | Redis 熔断降级到 MySQL |
| **Flyway** | V7, V8 | V7 创建秒杀相关表，V8 创建补偿表 |

---

## 7. teacircle — 茶友圈社交

**目录**: `../src/main/java/com/xytgy/teamallbackend/module/teacircle/`

**主要职责**: 茶友圈帖子发布/点赞/评论、话题管理、关注关系、活动管理、站内通知、图片上传

### Controller

| 类名 | 文件路径 |
|------|---------|
| TeaCirclePostController | `../src/main/java/com/xytgy/teamallbackend/module/teacircle/controller/TeaCirclePostController.java` |
| TeaCircleTopicController | `../src/main/java/com/xytgy/teamallbackend/module/teacircle/controller/TeaCircleTopicController.java` |
| TeaCircleFollowController | `../src/main/java/com/xytgy/teamallbackend/module/teacircle/controller/TeaCircleFollowController.java` |
| TeaCircleNotificationController | `../src/main/java/com/xytgy/teamallbackend/module/teacircle/controller/TeaCircleNotificationController.java` |
| TeaCircleCampaignController | `../src/main/java/com/xytgy/teamallbackend/module/teacircle/controller/TeaCircleCampaignController.java` |
| TeaCircleFileController | `../src/main/java/com/xytgy/teamallbackend/module/teacircle/controller/TeaCircleFileController.java` |

### Service

| 类名 | 文件路径 |
|------|---------|
| TeaPostService / TeaPostServiceImpl | `../src/main/java/com/xytgy/teamallbackend/module/teacircle/service/TeaPostService.java` / `impl/TeaPostServiceImpl.java` |
| TeaCommentService / TeaCommentServiceImpl | `../src/main/java/com/xytgy/teamallbackend/module/teacircle/service/TeaCommentService.java` / `impl/TeaCommentServiceImpl.java` |
| TeaTopicService / TeaTopicServiceImpl | `../src/main/java/com/xytgy/teamallbackend/module/teacircle/service/TeaTopicService.java` / `impl/TeaTopicServiceImpl.java` |
| TeaFollowService / TeaFollowServiceImpl | `../src/main/java/com/xytgy/teamallbackend/module/teacircle/service/TeaFollowService.java` / `impl/TeaFollowServiceImpl.java` |
| TeaNotificationService / TeaNotificationServiceImpl | `../src/main/java/com/xytgy/teamallbackend/module/teacircle/service/TeaNotificationService.java` / `impl/TeaNotificationServiceImpl.java` |
| TeaCampaignService / TeaCampaignServiceImpl | `../src/main/java/com/xytgy/teamallbackend/module/teacircle/service/TeaCampaignService.java` / `impl/TeaCampaignServiceImpl.java` |
| CommentLikeService / CommentLikeServiceImpl | `../src/main/java/com/xytgy/teamallbackend/module/teacircle/service/CommentLikeService.java` / `impl/CommentLikeServiceImpl.java` |

### Mapper

| 类名 | 文件路径 |
|------|---------|
| TeaPostMapper | `../src/main/java/com/xytgy/teamallbackend/module/teacircle/mapper/TeaPostMapper.java` |
| TeaCommentMapper | `../src/main/java/com/xytgy/teamallbackend/module/teacircle/mapper/TeaCommentMapper.java` |
| TeaTopicMapper | `../src/main/java/com/xytgy/teamallbackend/module/teacircle/mapper/TeaTopicMapper.java` |
| TeaPostTopicMapper | `../src/main/java/com/xytgy/teamallbackend/module/teacircle/mapper/TeaPostTopicMapper.java` |
| TeaFollowMapper | `../src/main/java/com/xytgy/teamallbackend/module/teacircle/mapper/TeaFollowMapper.java` |
| TeaLikeMapper | `../src/main/java/com/xytgy/teamallbackend/module/teacircle/mapper/TeaLikeMapper.java` |
| CommentLikeMapper | `../src/main/java/com/xytgy/teamallbackend/module/teacircle/mapper/CommentLikeMapper.java` |
| TeaNotificationMapper | `../src/main/java/com/xytgy/teamallbackend/module/teacircle/mapper/TeaNotificationMapper.java` |
| TeaCampaignMapper | `../src/main/java/com/xytgy/teamallbackend/module/teacircle/mapper/TeaCampaignMapper.java` |

### Entity / DTO / VO

- **Entity**: TeaPost, TeaComment, TeaTopic, TeaPostTopic, TeaFollow, TeaLike, CommentLike, TeaNotification, TeaCampaign
- **DTO**: TeaPostAddRequest, TeaCommentAddRequest
- **VO**: TeaPostVO, TeaCommentVO, TeaTopicVO, TeaCampaignVO, TeaNotificationVO, TeaCircleImageUploadVO, AuthorVO, SimpleUserVO, UserProfileVO

### 跨组件依赖

| 组件 | 使用方式 | 说明 |
|------|---------|------|
| **RocketMQ** | 生产：`TOPIC_TEA_NOTIFICATION` (TAG: `LIKE`, `COMMENT`) | 点赞/评论异步通知 |
| **Redis** | `RedisUtils` / `StringRedisTemplate`（TeaTopicServiceImpl） | 话题热度缓存 |
| **Flyway** | V2, V10 | V2 创建茶友圈核心表，V10 添加 follow/like 表 |

---

## 8. chat — 即时聊天

**目录**: `../src/main/java/com/xytgy/teamallbackend/module/chat/`

**主要职责**: 用户与商家/客服的即时聊天（WebSocket 实时推送 + MQ 异步分发）、会话管理、消息已读标记

### Controller

| 类名 | 文件路径 |
|------|---------|
| ChatController | `../src/main/java/com/xytgy/teamallbackend/module/chat/controller/ChatController.java` |

### Service

| 类名 | 文件路径 |
|------|---------|
| ChatService | `../src/main/java/com/xytgy/teamallbackend/module/chat/service/ChatService.java` |
| ChatServiceImpl | `../src/main/java/com/xytgy/teamallbackend/module/chat/service/impl/ChatServiceImpl.java` |

### Mapper

| 类名 | 文件路径 |
|------|---------|
| ChatMessageMapper | `../src/main/java/com/xytgy/teamallbackend/module/chat/mapper/ChatMessageMapper.java` |
| ChatSessionMapper | `../src/main/java/com/xytgy/teamallbackend/module/chat/mapper/ChatSessionMapper.java` |

### Entity / DTO / VO

- **Entity**: ChatMessage, ChatSession
- **DTO**: ChatSendRequest, ChatReadRequest, MerchantChatSendRequest, MerchantChatReadRequest
- **VO**: ChatMessageVO, ChatSessionVO

### 跨组件依赖

| 组件 | 使用方式 | 说明 |
|------|---------|------|
| **WebSocket** | `ChatWebSocketHandler` | 实时消息推送，连接管理通过 `WebSocketSessionRegistry` |
| **RocketMQ** | 生产：`TOPIC_CHAT_MESSAGE` (TAG: `MSG_DISPATCH`) | 消息异步分发到目标用户 |
| **Redis** | `StringRedisTemplate`（WebSocketSessionRegistry） | WebSocket 会话注册表 |
| **Flyway** | V2 | V2 创建聊天相关表 |

---

## 9. favorite — 收藏夹

**目录**: `../src/main/java/com/xytgy/teamallbackend/module/favorite/`

**主要职责**: 商品收藏与取消收藏、收藏列表查询

### Controller

| 类名 | 文件路径 |
|------|---------|
| FavoriteController | `../src/main/java/com/xytgy/teamallbackend/module/favorite/controller/FavoriteController.java` |

### Service

| 类名 | 文件路径 |
|------|---------|
| FavoriteService | `../src/main/java/com/xytgy/teamallbackend/module/favorite/service/FavoriteService.java` |
| FavoriteServiceImpl | `../src/main/java/com/xytgy/teamallbackend/module/favorite/service/impl/FavoriteServiceImpl.java` |

### Mapper

| 类名 | 文件路径 |
|------|---------|
| FavoriteMapper | `../src/main/java/com/xytgy/teamallbackend/module/favorite/mapper/FavoriteMapper.java` |

### Entity / DTO / VO

- **Entity**: Favorite
- **DTO**: FavoriteAddRequest, FavoriteRemoveRequest
- **VO**: FavoriteItemVO

### 跨组件依赖

| 组件 | 使用方式 | 说明 |
|------|---------|------|
| **Flyway** | V10 | V10 创建 favorite 表 |

---

## 10. feedback — 意见反馈

**目录**: `../src/main/java/com/xytgy/teamallbackend/module/feedback/`

**主要职责**: 用户提交意见反馈、管理员查看与处理反馈

### Controller

| 类名 | 文件路径 |
|------|---------|
| FeedbackController | `../src/main/java/com/xytgy/teamallbackend/module/feedback/controller/FeedbackController.java` |

### Service

| 类名 | 文件路径 |
|------|---------|
| FeedbackService | `../src/main/java/com/xytgy/teamallbackend/module/feedback/service/FeedbackService.java` |
| FeedbackServiceImpl | `../src/main/java/com/xytgy/teamallbackend/module/feedback/service/impl/FeedbackServiceImpl.java` |

### Mapper

| 类名 | 文件路径 |
|------|---------|
| FeedbackMapper | `../src/main/java/com/xytgy/teamallbackend/module/feedback/mapper/FeedbackMapper.java` |

### Entity / DTO / VO

- **Entity**: Feedback
- **DTO**: FeedbackSubmitRequest, FeedbackStatusRequest
- **VO**: FeedbackVO

### 跨组件依赖

| 组件 | 使用方式 | 说明 |
|------|---------|------|
| **Flyway** | V1 | V1 创建 feedback 表 |

---

## 11. support — 客服工单

**目录**: `../src/main/java/com/xytgy/teamallbackend/module/support/`

**主要职责**: 客服工单创建、状态流转、工单详情查询

### Controller

| 类名 | 文件路径 |
|------|---------|
| SupportController | `../src/main/java/com/xytgy/teamallbackend/module/support/controller/SupportController.java` |

### Service

| 类名 | 文件路径 |
|------|---------|
| SupportService | `../src/main/java/com/xytgy/teamallbackend/module/support/service/SupportService.java` |
| SupportServiceImpl | `../src/main/java/com/xytgy/teamallbackend/module/support/service/impl/SupportServiceImpl.java` |

### Mapper

| 类名 | 文件路径 |
|------|---------|
| SupportTicketMapper | `../src/main/java/com/xytgy/teamallbackend/module/support/mapper/SupportTicketMapper.java` |

### Entity / DTO / VO

- **Entity**: SupportTicket
- **DTO**: SupportCreateRequest
- **VO**: SupportTicketVO

### 跨组件依赖

| 组件 | 使用方式 | 说明 |
|------|---------|------|
| **Flyway** | V1 | V1 创建 support_ticket 表 |

---

## 12. upload — 文件上传

**目录**: `../src/main/java/com/xytgy/teamallbackend/module/upload/`

**主要职责**: 通用图片文件上传（阿里云 OSS），含文件类型校验与魔术字安全检测

### Controller

| 类名 | 文件路径 |
|------|---------|
| FileUploadController | `../src/main/java/com/xytgy/teamallbackend/module/upload/controller/FileUploadController.java` |

### Service

无独立 Service，直接使用 `AliyunOSSUtils` 工具类完成上传。

### Mapper

无

### Entity / DTO / VO

无

### 跨组件依赖

| 组件 | 使用方式 | 说明 |
|------|---------|------|
| **阿里云 OSS** | `AliyunOSSUtils` | 文件存储 |

---

## 跨模块基础设施

### mq/ — 消息队列

**目录**: `../src/main/java/com/xytgy/teamallbackend/mq/`

提供 RocketMQ 消息生产/消费的统一基础设施，包含本地重试补偿机制。

| 类名 | 说明 | 文件路径 |
|------|------|---------|
| **常量** | | |
| MqConstants | Topic/Tag 常量定义 | `../src/main/java/com/xytgy/teamallbackend/mq/constant/MqConstants.java` |
| **配置** | | |
| RocketMQConfig | RocketMQ 连接配置 | `../src/main/java/com/xytgy/teamallbackend/mq/config/RocketMQConfig.java` |
| FlashSaleCacheManager | 秒杀本地库存缓存 + Redis Pub/Sub 广播 | `../src/main/java/com/xytgy/teamallbackend/mq/config/FlashSaleCacheManager.java` |
| **生产者** | | |
| MqProducer | 统一消息生产者（支持事务感知、延迟消息、本地重试表） | `../src/main/java/com/xytgy/teamallbackend/mq/producer/MqProducer.java` |
| NoOpMqProducer | 空实现（RocketMQ 不可用时降级） | `../src/main/java/com/xytgy/teamallbackend/mq/producer/NoOpMqProducer.java` |
| **消费者** | | |
| FlashOrderConsumer | 秒杀订单消费（批量落库、幂等、重试） | `../src/main/java/com/xytgy/teamallbackend/mq/consumer/FlashOrderConsumer.java` |
| OrderTimeoutConsumer | 订单超时自动取消 | `../src/main/java/com/xytgy/teamallbackend/mq/consumer/OrderTimeoutConsumer.java` |
| PaymentNotifyConsumer | 支付成功异步通知 | `../src/main/java/com/xytgy/teamallbackend/mq/consumer/PaymentNotifyConsumer.java` |
| ChatMessageConsumer | 聊天消息 WebSocket 分发 | `../src/main/java/com/xytgy/teamallbackend/mq/consumer/ChatMessageConsumer.java` |
| TeaNotificationConsumer | 茶友圈通知创建 | `../src/main/java/com/xytgy/teamallbackend/mq/consumer/TeaNotificationConsumer.java` |
| **重试调度** | | |
| MqRetryScheduler | 本地重试表定时补偿（指数退避 10→30→90→270 秒） | `../src/main/java/com/xytgy/teamallbackend/mq/scheduler/MqRetryScheduler.java` |
| **工具** | | |
| IdempotentUtil | 幂等工具 | `../src/main/java/com/xytgy/teamallbackend/mq/util/IdempotentUtil.java` |
| **实体/Mapper** | | |
| MqRetryRecord / MqRetryRecordMapper | 本地重试记录表 | `../src/main/java/com/xytgy/teamallbackend/mq/entity/MqRetryRecord.java` / `mapper/MqRetryRecordMapper.java` |
| MqConsumedRecord / MqConsumedRecordMapper | 消费幂等记录表 | `../src/main/java/com/xytgy/teamallbackend/mq/entity/MqConsumedRecord.java` / `mapper/MqConsumedRecordMapper.java` |

### config/ — 配置类

**目录**: `../src/main/java/com/xytgy/teamallbackend/config/`

| 类名 | 说明 | 文件路径 |
|------|------|---------|
| **安全** | | |
| SecurityConfig | Spring Security 全局配置 | `../src/main/java/com/xytgy/teamallbackend/config/security/SecurityConfig.java` |
| JwtAuthenticationFilter | JWT 认证过滤器（Redis 校验用户缓存） | `../src/main/java/com/xytgy/teamallbackend/config/security/JwtAuthenticationFilter.java` |
| CustomAccessDeniedHandler | 403 无权限处理 | `../src/main/java/com/xytgy/teamallbackend/config/security/CustomAccessDeniedHandler.java` |
| CustomAuthEntryPoint | 401 未认证处理 | `../src/main/java/com/xytgy/teamallbackend/config/security/CustomAuthEntryPoint.java` |
| **数据源** | | |
| DynamicDataSourceConfig | 动态数据源（读写分离）配置 | `../src/main/java/com/xytgy/teamallbackend/config/datasource/DynamicDataSourceConfig.java` |
| DynamicRoutingDataSource | 读写分离路由 | `../src/main/java/com/xytgy/teamallbackend/config/datasource/DynamicRoutingDataSource.java` |
| DynamicDataSourceContextHolder | 数据源上下文持有 | `../src/main/java/com/xytgy/teamallbackend/config/datasource/DynamicDataSourceContextHolder.java` |
| DataSourceType | 数据源类型枚举 | `../src/main/java/com/xytgy/teamallbackend/config/datasource/DataSourceType.java` |
| ReadOnly | 只读注解 | `../src/main/java/com/xytgy/teamallbackend/config/datasource/ReadOnly.java` |
| ReadOnlyAspect | 只读切面 | `../src/main/java/com/xytgy/teamallbackend/config/datasource/ReadOnlyAspect.java` |
| **WebSocket** | | |
| WebSocketConfig | WebSocket 端点注册 | `../src/main/java/com/xytgy/teamallbackend/config/websocket/WebSocketConfig.java` |
| ChatWebSocketHandler | 聊天 WebSocket 消息处理 | `../src/main/java/com/xytgy/teamallbackend/config/websocket/ChatWebSocketHandler.java` |
| WebSocketSessionRegistry | WebSocket 会话注册表（Redis 存储在线状态） | `../src/main/java/com/xytgy/teamallbackend/config/websocket/WebSocketSessionRegistry.java` |
| **其他** | | |
| ElasticsearchConfig | ES 连接配置 | `../src/main/java/com/xytgy/teamallbackend/config/ElasticsearchConfig.java` |
| RedissonConfig | Redisson 分布式锁配置 | `../src/main/java/com/xytgy/teamallbackend/config/RedissonConfig.java` |
| AlipayConfig | 支付宝 SDK 配置 | `../src/main/java/com/xytgy/teamallbackend/config/AlipayConfig.java` |
| AsyncThreadPoolConfig | 异步线程池配置 | `../src/main/java/com/xytgy/teamallbackend/config/AsyncThreadPoolConfig.java` |
| FlywayRepairConfig / FlywayRepairRunner | Flyway 修复配置 | `../src/main/java/com/xytgy/teamallbackend/config/FlywayRepairConfig.java` / `FlywayRepairRunner.java` |
| NacosConfigListener | Nacos 配置热更新监听 | `../src/main/java/com/xytgy/teamallbackend/config/NacosConfigListener.java` |
| WebConfig | Web MVC 配置 | `../src/main/java/com/xytgy/teamallbackend/config/WebConfig.java` |
| OpenApiConfig | Swagger/OpenAPI 配置 | `../src/main/java/com/xytgy/teamallbackend/config/OpenApiConfig.java` |
| XssFilter | XSS 过滤器 | `../src/main/java/com/xytgy/teamallbackend/config/XssFilter.java` |
| BrowserCacheConfig | 浏览器缓存头配置 | `../src/main/java/com/xytgy/teamallbackend/config/BrowserCacheConfig.java` |
| TrustedProxyConfig | 可信代理配置 | `../src/main/java/com/xytgy/teamallbackend/config/TrustedProxyConfig.java` |
| MyMetaObjectHandler | MyBatis-Plus 自动填充（createTime/updateTime） | `../src/main/java/com/xytgy/teamallbackend/config/MyMetaObjectHandler.java` |

### common/ — 公共类

**目录**: `../src/main/java/com/xytgy/teamallbackend/common/`

| 类名 | 说明 | 文件路径 |
|------|------|---------|
| Result | 统一响应封装 | `../src/main/java/com/xytgy/teamallbackend/common/Result.java` |
| ResultCode | 响应状态码枚举 | `../src/main/java/com/xytgy/teamallbackend/common/ResultCode.java` |
| PageResult | 分页结果封装 | `../src/main/java/com/xytgy/teamallbackend/common/PageResult.java` |
| BaseController | Controller 基类（currentUserId/currentShopId） | `../src/main/java/com/xytgy/teamallbackend/common/BaseController.java` |
| UserRole | 用户角色枚举 | `../src/main/java/com/xytgy/teamallbackend/common/UserRole.java` |
| CopyMapper | MapStruct 复制接口 | `../src/main/java/com/xytgy/teamallbackend/common/mapstruct/CopyMapper.java` |

### utils/ — 工具类

**目录**: `../src/main/java/com/xytgy/teamallbackend/utils/`

| 类名 | 说明 | 文件路径 |
|------|------|---------|
| RedisUtils | Redis 通用操作（缓存穿透/雪崩/击穿防护、分布式锁、Lua 脚本执行） | `../src/main/java/com/xytgy/teamallbackend/utils/RedisUtils.java` |
| JwtUtils | JWT 令牌生成与解析 | `../src/main/java/com/xytgy/teamallbackend/utils/JwtUtils.java` |
| PasswordUtil | 密码加密与验证（BCrypt） | `../src/main/java/com/xytgy/teamallbackend/utils/PasswordUtil.java` |
| AliyunOSSUtils | 阿里云 OSS 文件上传 | `../src/main/java/com/xytgy/teamallbackend/utils/AliyunOSSUtils.java` |
| FileMagicUtils | 文件魔术字节校验（防伪造 Content-Type） | `../src/main/java/com/xytgy/teamallbackend/utils/FileMagicUtils.java` |
| RequestUtils | HTTP 请求工具（获取客户端 IP 等） | `../src/main/java/com/xytgy/teamallbackend/utils/RequestUtils.java` |

### security/ — 安全工具

**目录**: `../src/main/java/com/xytgy/teamallbackend/security/`

| 类名 | 说明 | 文件路径 |
|------|------|---------|
| SecurityUtils | 安全上下文工具（获取当前用户 ID/角色） | `../src/main/java/com/xytgy/teamallbackend/security/SecurityUtils.java` |
| JwtAuthenticationToken | JWT 认证令牌对象 | `../src/main/java/com/xytgy/teamallbackend/security/JwtAuthenticationToken.java` |
| SecurityConstants | 安全相关常量 | `../src/main/java/com/xytgy/teamallbackend/security/SecurityConstants.java` |

### ratelimit/ — 速率限制

**目录**: `../src/main/java/com/xytgy/teamallbackend/ratelimit/`

| 类名 | 说明 | 文件路径 |
|------|------|---------|
| RateLimitService | 登录双维度限流（账号 + IP，Redis 计数器） | `../src/main/java/com/xytgy/teamallbackend/ratelimit/RateLimitService.java` |

### filter/ — 全局过滤器

**目录**: `../src/main/java/com/xytgy/teamallbackend/filter/`

| 类名 | 说明 | 文件路径 |
|------|------|---------|
| GlobalRateLimitFilter | 全局 API 限流过滤器（Redis 滑动窗口） | `../src/main/java/com/xytgy/teamallbackend/filter/GlobalRateLimitFilter.java` |
| CacheHeaderFilter | 浏览器缓存头过滤器 | `../src/main/java/com/xytgy/teamallbackend/filter/CacheHeaderFilter.java` |

### exception/ — 异常处理

**目录**: `../src/main/java/com/xytgy/teamallbackend/exception/`

| 类名 | 说明 | 文件路径 |
|------|------|---------|
| ServiceException | 业务异常 | `../src/main/java/com/xytgy/teamallbackend/exception/ServiceException.java` |
| GlobalExceptionHandler | 全局异常处理器 | `../src/main/java/com/xytgy/teamallbackend/exception/GlobalExceptionHandler.java` |

---

## 跨组件依赖总览

### Redis

**工具类**: `RedisUtils`（`../src/main/java/com/xytgy/teamallbackend/utils/RedisUtils.java`）

| 模块 | 用途 |
|------|------|
| user | 用户信息缓存（防穿透/雪崩/击穿）、JWT Token 黑名单 |
| product | ES 搜索结果缓存、搜索建议缓存、热门搜索词缓存、同步状态与分布式锁 |
| flashsale | 库存原子扣减（Lua）、滑动窗口限流（Lua）、验证码、黑/白名单、行为分析、活动信息 Hash 缓存、Pub/Sub 库存广播 |
| order | 支付回调幂等校验 |
| teacircle | 话题热度缓存 |
| chat | WebSocket 会话注册表（在线状态） |
| ratelimit | 登录双维度限流（账号 + IP） |
| filter | 全局 API 限流 |
| config | JWT 认证过滤器用户缓存校验 |

**Lua 脚本**:

| 文件 | 路径 | 使用方 |
|------|------|--------|
| flash_deduct.lua | `../src/main/resources/lua/flash_deduct.lua` | FlashSaleCoreService（秒杀库存扣减） |
| sliding_window_limit.lua | `../src/main/resources/lua/sliding_window_limit.lua` | FlashSaleRateLimiter（滑动窗口限流） |
| flash_refund.lua | `../src/main/resources/lua/flash_refund.lua` | FlashSaleAdminService（库存回补） |

### RocketMQ

**配置类**: `RocketMQConfig`（`../src/main/java/com/xytgy/teamallbackend/mq/config/RocketMQConfig.java`）

| Topic | Tag | 生产方 | 消费方 |
|-------|-----|--------|--------|
| `TOPIC_FLASH_ORDER` | `FLASH_ORDER` | FlashSaleCoreService | FlashOrderConsumer（批量落库秒杀订单） |
| `TOPIC_ORDER_TIMEOUT` | `TIMEOUT_CANCEL` | OrdersServiceImpl | OrderTimeoutConsumer（订单超时取消） |
| `TOPIC_PAYMENT_NOTIFY` | `PAY_SUCCESS` | PaymentController | PaymentNotifyConsumer（支付成功通知） |
| `TOPIC_CHAT_MESSAGE` | `MSG_DISPATCH` | ChatServiceImpl | ChatMessageConsumer（WebSocket 分发消息） |
| `TOPIC_TEA_NOTIFICATION` | `LIKE`, `COMMENT` | TeaPostServiceImpl / TeaCommentServiceImpl | TeaNotificationConsumer（创建站内通知） |

### WebSocket

**配置类**: `WebSocketConfig`（`../src/main/java/com/xytgy/teamallbackend/config/websocket/WebSocketConfig.java`）

| 类名 | 路径 | 说明 |
|------|------|------|
| ChatWebSocketHandler | `../src/main/java/com/xytgy/teamallbackend/config/websocket/ChatWebSocketHandler.java` | 聊天消息实时推送 + 秒杀通知推送 |
| WebSocketSessionRegistry | `../src/main/java/com/xytgy/teamallbackend/config/websocket/WebSocketSessionRegistry.java` | 在线用户会话管理（Redis 存储） |

### Elasticsearch

**配置类**: `ElasticsearchConfig`（`../src/main/java/com/xytgy/teamallbackend/config/ElasticsearchConfig.java`）

| 类名 | 路径 | 说明 |
|------|------|------|
| ProductDocument | `../src/main/java/com/xytgy/teamallbackend/module/product/document/ProductDocument.java` | ES 索引 `products`，ik 中文分词 |
| ProductRepository | `../src/main/java/com/xytgy/teamallbackend/module/product/repository/ProductRepository.java` | ES 仓库接口 |
| ProductSearchService | `../src/main/java/com/xytgy/teamallbackend/module/product/service/ProductSearchService.java` | 全文搜索 + 搜索建议 + 热搜词 |
| ProductSyncService | `../src/main/java/com/xytgy/teamallbackend/module/product/service/ProductSyncService.java` | MySQL ↔ ES 全量/增量同步 |

### Flyway 迁移脚本

**目录**: `../src/main/resources/db/migration/`

| 版本 | 说明 | 主要涉及模块 |
|------|------|-------------|
| V1\_\_init.sql | 初始化建表（user, product, order, cart, feedback, support 等核心表） | user, product, cart, order, feedback, support |
| V2\_\_add\_tea\_circle\_and\_chat\_tables.sql | 茶友圈与聊天表 | teacircle, chat |
| V3\_\_seed\_data.sql | 种子数据 | user, product |
| V4\_\_add\_performance\_indexes.sql | 性能索引 | cart, order |
| V5\_\_add\_is\_deleted\_to\_tables.sql | 逻辑删除字段 | 多模块 |
| V6\_\_optimize\_query\_indexes.sql | 查询索引优化 | product, order |
| V7\_\_add\_flash\_sale\_tables.sql | 秒杀活动表 | flashsale |
| V8\_\_add\_flash\_sale\_compensation.sql | 秒杀补偿表 | flashsale |
| V9\_\_add\_missing\_indexes.sql | 补充索引 | cart, order, product |
| V10\_\_add\_banner\_category\_follow\_like\_tables.sql | Banner/关注/收藏/点赞表 | banner, shop, favorite, teacircle |
| V11\_\_create\_mq\_retry\_record.sql | MQ 重试记录表 | mq |
| V12\_\_create\_mq\_consumed\_record.sql | MQ 消费幂等记录表 | mq |

---

## banner 模块（附加）

> 任务清单中未包含 banner 模块，但项目中存在，此处补充记录。

**目录**: `../src/main/java/com/xytgy/teamallbackend/module/banner/`

**主要职责**: 轮播广告管理

| 类型 | 类名 | 文件路径 |
|------|------|---------|
| Controller | BannerController | `../src/main/java/com/xytgy/teamallbackend/module/banner/controller/BannerController.java` |
| Service | BannerService / BannerServiceImpl | `../src/main/java/com/xytgy/teamallbackend/module/banner/service/BannerService.java` / `impl/BannerServiceImpl.java` |
| Mapper | BannerMapper | `../src/main/java/com/xytgy/teamallbackend/module/banner/mapper/BannerMapper.java` |
| Entity | Banner | `../src/main/java/com/xytgy/teamallbackend/module/banner/entity/Banner.java` |
| 跨组件依赖 | Flyway V10 | 创建 banner 表 |
