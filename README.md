# CloudTea 茶商城后端项目 (tea-mall-backend)

## 1. 项目概述

**CloudTea (茶商城)** 是一个集茶叶商品交易、商家店铺管理、用户社交互动（茶友圈）、即时聊天以及秒杀活动于一体的综合性电商后端系统。

本项目旨在为热爱茶文化的买家提供一个选茶、购茶、论茶的一站式平台，同时为茶叶商户提供便捷的开店与订单管理工具。它解决传统茶叶交易中信息不透明、用户缺乏交流场景的问题，通过引入"茶友圈"模块，打造一个具有垂直社交属性的现代电商生态。

## 2. 核心功能

本项目采用模块化设计，共 12 个业务模块：

| 模块 | 说明 |
|------|------|
| **用户与认证 (User & Auth)** | 注册、登录（JWT）、角色管理（买家/商家/管理员）、个人资料、收货地址 |
| **商品与店铺 (Product & Shop)** | 商品浏览、评价、商家入驻、商品发布编辑、上下架管理 |
| **购物车 (Cart)** | 商品加购、数量修改、移出购物车 |
| **订单 (Order)** | 订单创建、支付、确认收货、取消、评价、商家发货、状态统计 |
| **秒杀 (Flash Sale)** | Redis Lua 原子扣库存、验证码防刷、MQ 异步下单、五层防护体系、对账任务 |
| **茶友圈 (Tea Circle)** | 图文动态、盖楼评论、点赞、关注、消息通知、话题与活动 |
| **即时聊天 (Chat)** | 基于 WebSocket 的买家-商家实时聊天、未读消息管理 |
| **收藏夹 (Favorite)** | 商品收藏、取消收藏及状态检查 |
| **意见反馈 (Feedback)** | 已登录用户或游客提交图文反馈 |
| **客服工单 (Support)** | 提交售后/咨询工单 |
| **文件上传 (Upload)** | 阿里云 OSS 文件上传（头像、图片等） |

## 3. 技术栈

| 类别 | 技术 | 说明 |
|------|------|------|
| 语言 | Java 17 | LTS 版本 |
| 核心框架 | Spring Boot 3.3.4 | 依赖注入、自动配置、Web 容器 |
| 持久层 | MyBatis-Plus 3.5.10 | 简化 CRUD、分页插件 |
| 数据库 | MySQL 8+ | InnoDB 引擎 |
| 缓存 | Redis + Caffeine | L1 本地缓存 + L2 分布式缓存、布隆过滤器防穿透 |
| 消息队列 | RocketMQ | 秒杀异步下单、订单超时、支付通知、聊天消息 |
| 安全 | Spring Security + JWT | 无状态鉴权、BCrypt 密码加密、登录限流 |
| 对象映射 | MapStruct 1.5.5 | 编译期生成 Entity/VO/DTO 映射代码 |
| API 文档 | Knife4j 4.5.0 | OpenAPI 3 在线接口调试 |
| 云服务 | Aliyun OSS SDK | 头像、动态图片等静态资源上传 |
| 数据库迁移 | Flyway | 版本化 SQL 迁移管理 |
| 工具库 | Lombok | 减少样板代码 |

**架构设计**：
项目采用单体 MVC 分层架构，按业务领域划分为 12 个子模块（`module`）。通过 `Controller` → `Service` → `Mapper` 的调用链保证代码高内聚低耦合。鉴权通过 `JwtAuthenticationFilter`（Spring Security 过滤器链）统一拦截并解析 Token。

## 4. 项目架构与目录结构

```text
tea-mall-backend
├── src/main/java/com/xytgy/teamallbackend
│   ├── common/              # 公共类 (Result、PageResult、ResultCode、UserRole、CopyMapper)
│   ├── config/              # 配置类
│   │   ├── datasource/      #   多数据源动态切换（读写分离）
│   │   ├── mq/              #   RocketMQ 生产者/消费者（秒杀订单、聊天、通知等）
│   │   ├── security/        #   Spring Security 配置、JWT 过滤器
│   │   └── websocket/       #   WebSocket 聊天配置
│   ├── exception/           # 全局异常处理器 (GlobalExceptionHandler、ServiceException)
│   ├── security/            # 安全工具 (SecurityUtils、SecurityConstants、JwtAuthenticationToken)
│   ├── utils/               # 工具类 (RedisUtils、JwtUtils、BloomFilterManager、DistributedLock、RateLimitService 等)
│   └── module/              # 12 个业务模块（每个模块含 controller/dto/entity/repository/service/vo）
│       ├── user/            #   用户认证、资料、收货地址
│       ├── product/         #   商品浏览、评价、商家商品管理
│       ├── shop/            #   店铺开通与信息管理
│       ├── cart/            #   购物车
│       ├── order/           #   订单创建、流转、支付、状态统计
│       ├── flashsale/       #   秒杀活动、验证码、限流、对账
│       ├── teacircle/       #   茶友圈动态、评论、点赞、关注、通知、话题
│       ├── chat/            #   即时聊天（WebSocket）
│       ├── favorite/        #   商品收藏夹
│       ├── feedback/        #   意见反馈
│       ├── support/         #   客服工单
│       └── upload/          #   文件上传
├── src/main/resources/
│   ├── db/migration/        # Flyway 数据库迁移脚本（V1~V8）
│   ├── lua/                 # Redis Lua 脚本（秒杀扣减/回补、滑动窗口限流）
│   ├── mapper/              # MyBatis XML 映射文件
│   └── application.yaml     # 主配置文件
└── pom.xml                  # Maven 依赖管理
```

## 5. 运行与使用方式

### 前置条件
1. JDK 17 或更高版本
2. Maven 3.6+
3. MySQL 8.0 数据库
4. Redis 服务
5. RocketMQ（秒杀、聊天等功能需要）

### 启动步骤

1.  **数据库初始化**：
    *   在 MySQL 中创建数据库：`CREATE DATABASE cloud_tea_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`
    *   项目启动时 Flyway 自动执行 `src/main/resources/db/migration/` 下的迁移脚本。

2.  **配置环境变量**：

    | 变量 | 说明 | 默认值 |
    |------|------|--------|
    | `DB_HOST` | MySQL 主机 | localhost |
    | `DB_PORT` | MySQL 端口 | 3306 |
    | `DB_USERNAME` | MySQL 用户名 | - |
    | `DB_PASSWORD` | MySQL 密码 | - |
    | `REDIS_HOST` | Redis 主机 | redis |
    | `REDIS_PORT` | Redis 端口 | 6379 |
    | `REDIS_PASSWORD` | Redis 密码 | - |
    | `JWT_SECRET` | JWT 密钥（≥32字符） | - |
    | `ROCKETMQ_NAMESRV` | RocketMQ NameServer | localhost:9876 |
    | `ALIYUN_OSS_*` | 阿里云 OSS 配置 | - |

3.  **启动项目**：
    ```bash
    ./mvnw spring-boot:run
    ```
    或在 IDEA 中运行 `TeaMallBackendApplication.java`。

4.  **API 文档**：
    项目启动后访问 `http://localhost:8081/doc.html`（需设置 `KNIFE4J_ENABLE=true`）。

### 运行测试
```bash
./mvnw test
```

## 6. 项目目的与价值

**建设动机**：
随着新中式茶饮的崛起和茶文化的年轻化，传统的单一茶叶销售平台已经无法满足用户对"交流与分享"的需求。CloudTea 的诞生是为了打破"买完即走"的传统电商模式。

**应用场景与目标用户**：
*   **茶叶消费者**：可以轻松选购来自不同商家的茶叶，并在"茶友圈"分享品茶心得、晒茶具，结交同好。
*   **茶农与茶商**：提供了一个极低门槛的入驻平台。商家不仅能管理商品和处理订单，还能通过茶友圈发布新品预热、普及茶知识，直接触达精准客户群体，实现私域流量沉淀。

**实际价值**：
本项目通过将 **B2C/C2C 交易链路** 与 **UGC 内容社区** 深度融合，大幅提升了用户的留存率与复购率。技术上采用成熟的 Spring Boot 3 + Vue 3 (前端) 架构，代码规范清晰，非常适合作为现代全栈电商+社交复合型应用的商业级脚手架或二次开发基座。
