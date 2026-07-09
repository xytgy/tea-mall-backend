# 茶叶电商后端项目 — 技术栈全景台账

> 生成时间: 2026-07-09  
> 项目路径: `/Users/xytgy/Downloads/software/tea-mall-backend`  
> 覆盖文件: pom.xml (root/gateway/demo-service), Dockerfile, docker-compose*.yml, application*.yaml, bootstrap*.yaml, TECH_STACK.md, .github/workflows/ci.yml, skywalking-agent/, docker/ 配置

---

## 1. 编程语言与运行时

| 技术 | 版本 | 用途说明 | 来源文件 |
|------|------|----------|----------|
| Java | 17 (LTS) | 主编程语言，支持 Records、Pattern Matching 等新特性 | pom.xml `<java.version>17</java.version>` |
| Maven | 3.9.15 | 项目构建与依赖管理 (通过 Wrapper 使用) | `.mvn/wrapper/maven-wrapper.properties` |
| Maven Wrapper | 3.3.4 | 统一团队 Maven 版本，无需手动安装 | `.mvn/wrapper/maven-wrapper.properties` |
| JVM (生产) | G1GC | `-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200` | `Dockerfile` ENV JAVA_OPTS |
| JVM (RocketMQ) | - | `-Xms256m -Xmx256m` (NameServer/Broker/Slave) | `docker-compose.yml` |
| JVM (Nacos) | - | `JVM_XMS=256m JVM_XMX=256m JVM_XMN=128m` | `docker-compose.prodlike.yml` |
| JVM (ES) | - | `-Xms512m -Xmx512m` | `docker-compose.prodlike.yml` |

---

## 2. 后端框架

| 技术 | 版本 | 用途说明 | 来源文件 |
|------|------|----------|----------|
| Spring Boot | 3.3.4 | 主框架，内嵌 Tomcat，自动配置 | pom.xml parent |
| Spring Cloud | 2023.0.1 | 微服务治理 (依赖管理 BOM) | pom.xml `<dependencyManagement>` |
| Spring Cloud Alibaba | 2023.0.1.0 | 阿里巴巴微服务组件 (依赖管理 BOM) | pom.xml `<dependencyManagement>` |
| Spring Cloud Bootstrap | 由 BOM 管理 | 支持 bootstrap.yaml 上下文 (Spring Boot 3.x 需显式引入) | pom.xml |

---

## 3. 数据库层

| 技术 | 版本 | 用途说明 | 来源文件 |
|------|------|----------|----------|
| MySQL | 8.0 | 主数据库，存储核心业务数据 | `docker-compose.prodlike.yml` |
| MySQL Connector/J | 由 BOM 管理 | JDBC 驱动 | pom.xml |
| HikariCP | 由 BOM 管理 | 高性能连接池 (dev: max=30, prod: max=50) | application-dev/prod.yaml |
| MyBatis-Plus | 3.5.10.1 | ORM 框架，简化 CRUD，支持动态数据源 | pom.xml |
| Flyway Core | 由 BOM 管理 (~10.x) | 数据库 Schema 版本管理与自动迁移 | pom.xml |
| Flyway MySQL | 由 BOM 管理 | Flyway MySQL 方言支持 | pom.xml `<flyway.version>` |
| H2 Database | 由 BOM 管理 | 单元测试内存数据库 | pom.xml (scope: test) |
| 动态数据源 | 自研 | 读写分离 (DynamicDataSourceConfig) | `config/datasource/` |
| Canal | 配置文件存在 | MySQL Binlog CDC 同步 (canal.properties 已配置) | `docker/canal/canal.properties` |

---

## 4. 缓存层

| 层级 | 技术 | 版本 | 用途说明 |
|------|------|------|----------|
| L1 本地缓存 | Caffeine | 由 BOM 管理 | 进程内高速缓存，max=2048 条，60s 过期 |
| L2 分布式缓存 | Redis (Lettuce) | Redis 7 / Lettuce 由 BOM 管理 | 多实例共享，L1 未命中时降级查询 |
| 分布式锁 | Redisson | 3.31.0 | 订单防重复提交、库存扣减互斥 |
| 缓存防穿透 | Guava Bloom Filter | 32.1.3-jre | 大数据量场景下快速判断 key 是否存在 |
| L3 浏览器缓存 | HTTP Cache-Control/ETag | - | 按 API 路径分级缓存策略 |

**缓存配置明细:**

| 参数 | 值 | 说明 |
|------|-----|------|
| Redis Lettuce max-active | 32 | 最大活跃连接数 |
| Redis Lettuce max-idle | 16 | 最大空闲连接数 |
| Redis Lettuce min-idle | 8 | 最小空闲连接数 |
| Redis Lettuce max-wait | 2000ms | 最大等待时间 |
| Redis 命令超时 | 3000ms | Redis 命令超时 |
| Redis 连接超时 | 5000ms | Redis 连接超时 |
| Bloom Filter 期望插入量 | 1000000 | product/user 各 100 万 |
| Bloom Filter 误判率 | 0.01 | 1% 误判率 |

---

## 5. 消息中间件

| 技术 | 版本 | 用途说明 | 来源文件 |
|------|------|----------|----------|
| Apache RocketMQ | 5.3.0 (Docker 镜像) | 异步消息：订单超时取消、支付回调、秒杀下单、聊天分发 | docker-compose*.yml |
| RocketMQ Spring Boot Starter | 2.3.1 | RocketMQ 与 Spring Boot 集成 | pom.xml |
| RocketMQ Client | 5.3.0 | RocketMQ 客户端 | pom.xml |
| RocketMQ Common | 5.3.0 | RocketMQ 公共模块 | pom.xml |
| RocketMQ Remoting | 5.3.0 | RocketMQ 远程通信 | pom.xml |
| RocketMQ Tools | 5.3.0 | RocketMQ 管理工具 | pom.xml |
| Spring Kafka | 由 BOM 管理 | Kafka 消息集成 (开发环境 demo 用途) | pom.xml |
| Kafka (Docker) | 未在 compose 中定义 | 开发环境本地 Kafka (localhost:9092) | application-dev.yaml |

**RocketMQ 部署架构:**
- NameServer: 1 节点
- Broker Master: 1 节点 (brokerId=0, ASYNC_MASTER)
- Broker Slave: 1 节点 (读写分离)
- 消息保留: 72 小时
- 单条消息最大: 4MB
- 自动创建 Topic: 已关闭 (安全配置)

---

## 6. 搜索引擎

| 技术 | 版本 | 用途说明 | 来源文件 |
|------|------|----------|----------|
| Elasticsearch | 8.13.4 | 商品全文搜索 (ik 中文分词、高亮、聚合) | docker-compose.prodlike.yml |
| Spring Data Elasticsearch | 由 BOM 管理 | ES 与 Spring Boot 集成 | pom.xml |
| ES Security | xpack.security.enabled=true | 启用认证 (HTTP SSL 已关闭) | docker-compose.prodlike.yml |

---

## 7. 服务治理

| 技术 | 版本 | 用途说明 | 来源文件 |
|------|------|----------|----------|
| Nacos | v2.3.2 | 配置中心 + 服务注册与发现 (3 节点集群) | docker-compose.prodlike.yml |
| Nacos Discovery | 由 BOM 管理 | Spring Cloud Alibaba Nacos 服务发现 | pom.xml |
| Nacos Config | 由 BOM 管理 | Spring Cloud Alibaba Nacos 配置中心 | pom.xml |
| Sentinel | 由 BOM 管理 | Gateway 层流量控制与熔断降级 | gateway/pom.xml |
| Sentinel Gateway | 由 BOM 管理 | Sentinel Gateway 适配 | gateway/pom.xml |
| Spring Cloud LoadBalancer | 由 BOM 管理 | 负载均衡 (lb:// 协议支持) | gateway/pom.xml |

**Nacos 集群配置:**
- 节点: nacos1, nacos2, nacos3
- 模式: cluster
- 数据库: MySQL (nacos_config)
- 端口: 8848, 9848, 9849

---

## 8. 安全认证

| 技术 | 版本 | 用途说明 | 来源文件 |
|------|------|----------|----------|
| Spring Security | 由 BOM 管理 | 安全框架，拦截器链式认证授权 | pom.xml |
| JWT (jjwt-api) | 0.11.5 | 无状态 Token 认证，双 Token (access + refresh) | pom.xml |
| JWT (jjwt-impl) | 0.11.5 | JWT 实现 (runtime scope) | pom.xml |
| JWT (jjwt-jackson) | 0.11.5 | JWT Jackson 序列化 (runtime scope) | pom.xml |
| Kaptcha | 2.3.3 | 验证码生成 (排除了 javax.servlet-api 冲突) | pom.xml |
| XSS Filter | 自研 | 自定义 XssFilter，过滤请求中的 XSS 攻击脚本 | `config/XssFilter.java` |
| CORS 配置 | 自研 | 跨域资源共享，支持多域名白名单 | application.yaml `cors.*` |
| 可信代理配置 | 自研 | X-Forwarded-For 信任控制 | application.yaml `trusted.proxies` |

**JWT 配置:**
- Access Token 过期: 1,800,000ms (30 分钟)
- Refresh Token 过期: 604,800,000ms (7 天)
- 签名密钥: 环境变量注入 (JWT_SECRET)

---

## 9. 链路追踪与监控

| 技术 | 版本 | 用途说明 | 来源文件 |
|------|------|----------|----------|
| Apache SkyWalking Agent | 8.9.0 | APM 链路追踪 (Agent 方式采集) | `skywalking-agent/` 目录 |
| Zipkin | 2.24 | 分布式链路追踪 (Docker 部署) | docker-compose.prodlike.yml |
| Brave | 由 BOM 管理 | Zipkin 底层 Tracing 库 | (Zipkin 集成) |
| Spring Boot Actuator | 由 BOM 管理 | 健康检查端点 (health) | pom.xml |
| Micrometer Core | 由 BOM 管理 | 指标采集 | pom.xml |
| Prometheus | 配置文件存在 | 指标采集与可视化 | `docker/prometheus/prometheus.yml` |

**SkyWalking Agent 配置:**
- 服务名: tea-mall-backend
- 采样率: 每 3 秒 1 条
- Backend: localhost:11800
- 日志级别: INFO
- 可选插件: Kafka Reporter、Spring Cloud Gateway 3.x、Lettuce、MyBatis 3.x、Sentinel 等

**Actuator 配置:**
- 暴露端点: health
- Health 详情: when-authorized

**Prometheus 配置:**
- 抓取间隔: 15s
- 目标: host.docker.internal:8082/actuator/prometheus

---

## 10. 云服务组件

| 技术 | 版本 | 用途说明 | 来源文件 |
|------|------|----------|----------|
| 阿里云 OSS SDK | 3.15.0 | 文件上传 (头像、商品图片、茶友圈图片) | pom.xml |
| 支付宝 SDK | 4.39.231.ALL | 支付宝支付集成 (含模拟支付) | pom.xml |

**OSS 配置项:**
- endpoint: 环境变量 ALIYUN_OSS_ENDPOINT
- accessKeyId: 环境变量 ALIYUN_OSS_ACCESS_KEY_ID
- accessKeySecret: 环境变量 ALIYUN_OSS_ACCESS_KEY_SECRET
- bucketName: 环境变量 ALIYUN_OSS_BUCKET_NAME

---

## 11. 容器化与 DevOps

| 技术 | 版本 | 用途说明 | 来源文件 |
|------|------|----------|----------|
| Docker (构建) | maven:3.9.9-eclipse-temurin-17 | 多阶段构建，Builder 阶段 | Dockerfile |
| Docker (运行) | eclipse-temurin:17-jre-alpine | 最终运行镜像 (Alpine 精简) | Dockerfile |
| Docker Compose | - | 本地开发编排 (RocketMQ 集群) | docker-compose.yml |
| Docker Compose (prodlike) | - | 类生产环境编排 (全栈) | docker-compose.prodlike.yml |
| GitHub Actions | - | CI/CD 流水线 | .github/workflows/ci.yml |
| Docker Buildx | - | Docker 镜像构建 (CI 中) | ci.yml |
| Docker Hub | - | 镜像仓库 (push 到 Docker Hub) | ci.yml |

**Docker Compose (prodlike) 服务清单:**

| 服务 | 镜像 | 端口 | 说明 |
|------|------|------|------|
| mysql | mysql:8.0 | 3306 | 主数据库 |
| redis | redis:7 | 6379 | 缓存 |
| nacos1/2/3 | nacos/nacos-server:v2.3.2 | 8848/9848/9849 | Nacos 集群 |
| rocketmq-namesrv | apache/rocketmq:5.3.0 | 9876 | RocketMQ NameServer |
| rocketmq-broker | apache/rocketmq:5.3.0 | 10911/10909 | RocketMQ Broker Master |
| rocketmq-broker-slave | apache/rocketmq:5.3.0 | 10811/10809 | RocketMQ Broker Slave |
| elasticsearch | elasticsearch:8.13.4 | 9200 | 搜索引擎 |
| zipkin | openzipkin/zipkin:2.24 | 9411 | 链路追踪 |
| backend | 自构建 (Dockerfile) | 8082 | 后端应用 |

**CI/CD 流水线 (GitHub Actions):**
- 触发条件: push tag `v*`
- 步骤: Checkout → JDK 17 → Maven Build → Test → Package → Docker Build → Push to Docker Hub
- Docker 缓存: GitHub Actions Cache (gha)

---

## 12. 第三方 SDK 依赖 (完整清单)

### 12.1 Spring Boot Starter 依赖

| GroupId | ArtifactId | 版本 | Scope | 用途 |
|---------|-----------|------|-------|------|
| org.springframework.boot | spring-boot-starter-web | 由 BOM 管理 | compile | Web 框架 (内嵌 Tomcat) |
| org.springframework.boot | spring-boot-starter-jdbc | 由 BOM 管理 | compile | JDBC 支持 |
| org.springframework.boot | spring-boot-starter-data-redis | 由 BOM 管理 | compile | Redis 数据访问 |
| org.springframework.boot | spring-boot-starter-security | 由 BOM 管理 | compile | 安全框架 |
| org.springframework.boot | spring-boot-starter-validation | 由 BOM 管理 | compile | 参数校验 |
| org.springframework.boot | spring-boot-starter-websocket | 由 BOM 管理 | compile | WebSocket 支持 |
| org.springframework.boot | spring-boot-starter-aop | 由 BOM 管理 | compile | AOP 支持 |
| org.springframework.boot | spring-boot-starter-actuator | 由 BOM 管理 | compile | 健康检查与监控 (重复声明) |
| org.springframework.boot | spring-boot-starter-data-elasticsearch | 由 BOM 管理 | compile | ES 数据访问 |
| org.springframework.boot | spring-boot-starter-test | 由 BOM 管理 | test | 测试支持 |
| org.springframework.boot | spring-boot-devtools | 由 BOM 管理 | runtime, optional | 热重载开发 |
| org.springframework.boot | spring-boot-configuration-processor | 由 BOM 管理 | optional | 配置元数据生成 |

### 12.2 Spring Cloud 依赖

| GroupId | ArtifactId | 版本 | 用途 |
|---------|-----------|------|------|
| org.springframework.cloud | spring-cloud-dependencies | 2023.0.1 | BOM 依赖管理 |
| org.springframework.cloud | spring-cloud-starter-bootstrap | 由 BOM 管理 | Bootstrap 上下文支持 |
| org.springframework.cloud | spring-cloud-starter-gateway | 由 BOM 管理 | API 网关 (gateway 模块) |
| org.springframework.cloud | spring-cloud-starter-loadbalancer | 由 BOM 管理 | 负载均衡 (gateway 模块) |

### 12.3 Spring Cloud Alibaba 依赖

| GroupId | ArtifactId | 版本 | 用途 |
|---------|-----------|------|------|
| com.alibaba.cloud | spring-cloud-alibaba-dependencies | 2023.0.1.0 | BOM 依赖管理 |
| com.alibaba.cloud | spring-cloud-starter-alibaba-nacos-config | 由 BOM 管理 | Nacos 配置中心 |
| com.alibaba.cloud | spring-cloud-starter-alibaba-nacos-discovery | 由 BOM 管理 | Nacos 服务发现 |
| com.alibaba.cloud | spring-cloud-starter-alibaba-sentinel | 由 BOM 管理 | Sentinel 流量控制 (gateway) |
| com.alibaba.cloud | spring-cloud-alibaba-sentinel-gateway | 由 BOM 管理 | Sentinel Gateway 适配 (gateway) |

### 12.4 ORM 与数据层

| GroupId | ArtifactId | 版本 | 用途 |
|---------|-----------|------|------|
| com.baomidou | mybatis-plus-spring-boot3-starter | 3.5.10.1 | MyBatis-Plus ORM |
| com.mysql | mysql-connector-j | 由 BOM 管理 | MySQL JDBC 驱动 |
| com.h2database | h2 | 由 BOM 管理 | 测试内存数据库 |
| org.flywaydb | flyway-core | 由 BOM 管理 | 数据库迁移 |
| org.flywaydb | flyway-mysql | 由 flyway.version 管理 | Flyway MySQL 方言 |

### 12.5 缓存与分布式

| GroupId | ArtifactId | 版本 | 用途 |
|---------|-----------|------|------|
| com.github.ben-manes.caffeine | caffeine | 由 BOM 管理 | 本地缓存 |
| org.redisson | redisson-spring-boot-starter | 3.31.0 | 分布式锁 |
| com.google.guava | guava | 32.1.3-jre | 布隆过滤器 |

### 12.6 消息中间件

| GroupId | ArtifactId | 版本 | 用途 |
|---------|-----------|------|------|
| org.apache.rocketmq | rocketmq-spring-boot-starter | 2.3.1 | RocketMQ Spring 集成 |
| org.apache.rocketmq | rocketmq-client | 5.3.0 | RocketMQ 客户端 |
| org.apache.rocketmq | rocketmq-common | 5.3.0 | RocketMQ 公共模块 |
| org.apache.rocketmq | rocketmq-remoting | 5.3.0 | RocketMQ 远程通信 |
| org.apache.rocketmq | rocketmq-tools | 5.3.0 | RocketMQ 管理工具 |
| org.springframework.kafka | spring-kafka | 由 BOM 管理 | Kafka 集成 |

### 12.7 安全认证

| GroupId | ArtifactId | 版本 | 用途 |
|---------|-----------|------|------|
| io.jsonwebtoken | jjwt-api | 0.11.5 | JWT API |
| io.jsonwebtoken | jjwt-impl | 0.11.5 | JWT 实现 (runtime) |
| io.jsonwebtoken | jjwt-jackson | 0.11.5 | JWT Jackson (runtime) |
| pro.fessional | kaptcha | 2.3.3 | 验证码生成 |

### 12.8 对象映射与代码生成

| GroupId | ArtifactId | 版本 | 用途 |
|---------|-----------|------|------|
| org.projectlombok | lombok | 1.18.36 (properties) / 1.18.34 (processor) | 减少样板代码 |
| org.mapstruct | mapstruct | 1.5.5.Final | 类型安全对象映射 |
| org.projectlombok | lombok-mapstruct-binding | 0.2.0 | Lombok + MapStruct 桥接 |

### 12.9 API 文档

| GroupId | ArtifactId | 版本 | 用途 |
|---------|-----------|------|------|
| com.github.xiaoymin | knife4j-openapi3-jakarta-spring-boot-starter | 4.5.0 | OpenAPI 3.0 增强文档 |

### 12.10 云服务 SDK

| GroupId | ArtifactId | 版本 | 用途 |
|---------|-----------|------|------|
| com.aliyun.oss | aliyun-sdk-oss | 3.15.0 | 阿里云 OSS 文件上传 |
| com.alipay.sdk | alipay-sdk-java | 4.39.231.ALL | 支付宝支付集成 |

### 12.11 监控与可观测性

| GroupId | ArtifactId | 版本 | 用途 |
|---------|-----------|------|------|
| io.micrometer | micrometer-core | 由 BOM 管理 | 指标采集 |

### 12.12 弹性与容错

| GroupId | ArtifactId | 版本 | 用途 |
|---------|-----------|------|------|
| io.github.resilience4j | resilience4j-spring-boot3 | 2.20.0 | 熔断器 (Redis 故障降级) |

---

## 13. 前端/API文档

| 技术 | 版本 | 用途说明 | 来源文件 |
|------|------|----------|----------|
| Knife4j | 4.5.0 | OpenAPI 3.0 增强文档，中文界面 | pom.xml |
| Swagger 注解 | - | @Operation, @ApiResponse, @Schema 等 | Java 代码 |
| Spring WebFlux (Gateway) | 由 BOM 管理 | Gateway 使用 WebFlux 响应式栈 | gateway/pom.xml |

**API 文档访问控制:**
- dev 环境: knife4j.enable=true, docs.enabled=true
- prod 环境: knife4j.enable=false, docs.enabled=false

---

## 14. 开发工具

| 技术 | 版本 | 用途说明 | 来源文件 |
|------|------|----------|----------|
| Lombok | 1.18.36 | 减少样板代码 (@Data, @Builder, @Slf4j) | pom.xml |
| MapStruct | 1.5.5.Final | 类型安全的对象映射 | pom.xml |
| Spring DevTools | 由 BOM 管理 | 热重载开发 (dev 环境启用) | pom.xml |
| Maven Compiler Plugin | - | 编译插件 (含注解处理器配置) | pom.xml |
| Spring Boot Maven Plugin | - | 打包插件 | pom.xml |

**注解处理器配置 (maven-compiler-plugin):**
- Lombok 1.18.34
- MapStruct Processor 1.5.5.Final
- Lombok-MapStruct Binding 0.2.0
- Spring Boot Configuration Processor 3.3.4

---

## 15. 依赖统计与冲突分析

### 15.1 依赖总数统计

| 模块 | 直接依赖数 | BOM 管理依赖数 | 合计 |
|------|-----------|---------------|------|
| root (tea-mall-backend) | 27 | 2 BOM | ~40+ |
| gateway | 6 | 2 BOM | ~15+ |
| demo-service | 3 | 2 BOM | ~8+ |

### 15.2 发现的版本冲突与问题

| # | 问题 | 详情 | 严重程度 | 建议 |
|---|------|------|---------|------|
| 1 | **spring-boot-starter-actuator 重复声明** | pom.xml 第 227 行和第 232 行重复声明了 `spring-boot-starter-actuator` | 低 | 删除重复声明 |
| 2 | **Lombok 版本不一致** | `<properties>` 中声明 1.18.36，但 `maven-compiler-plugin` 注解处理器路径中使用 1.18.34 | 中 | 统一为 1.18.36 |
| 3 | **RocketMQ Spring Boot Starter 与 Client 版本潜在不兼容** | starter 2.3.1 内部依赖的 client 版本可能与显式声明的 5.3.0 不一致 | 中 | 建议验证兼容性或移除显式 client 依赖 |
| 4 | **Kafka 与 RocketMQ 并存** | 项目同时引入了 Spring Kafka 和 RocketMQ，Kafka 仅在 dev 配置和 demo 控制器中使用 | 低 | 如 Kafka 非必需，建议移除以减少复杂度 |
| 5 | **Canal 配置已准备但未部署** | `docker/canal/canal.properties` 配置文件存在，但 docker-compose 中未定义 Canal 服务 | 低 | 如需 CDC 同步，添加 Canal 服务到 compose |
| 6 | **Zipkin 与 SkyWalking 并存** | 项目同时配置了 Zipkin (Docker) 和 SkyWalking Agent，存在功能重叠 | 中 | 建议统一为一种链路追踪方案 |
| 7 | **Kaptcha 排除 javax.servlet-api** | kaptcha 2.3.3 排除了 `javax.servlet-api`，Spring Boot 3.x 使用 `jakarta.servlet-api`，排除正确 | 无 | 无需处理 |

### 15.3 版本管理策略

| 策略 | 说明 |
|------|------|
| Spring Boot BOM | 管理所有 Spring 相关依赖版本 (Tomcat, Lettuce, Caffeine, Flyway, H2, Micrometer 等) |
| Spring Cloud BOM | 管理 Spring Cloud 组件版本 |
| Spring Cloud Alibaba BOM | 管理 Nacos, Sentinel 等阿里组件版本 |
| 显式声明版本 | MyBatis-Plus, Lombok, MapStruct, jjwt, RocketMQ, Redisson, Guava, Resilience4j, Knife4j, OSS SDK, Alipay SDK |

---

## 16. 高并发与性能优化技术

| 技术/方案 | 说明 | 配置 |
|-----------|------|------|
| Redis Lua 脚本 | 秒杀库存原子扣减 | - |
| 分布式锁 (Redisson) | 订单防重复提交、库存扣减互斥 | default-wait-ms=200, default-lease-ms=30000 |
| 令牌桶限流 | Redis + Lua 实现，按 IP 限流 | capacity=100, rate=50 QPS |
| 布隆过滤器 | Guava Bloom Filter + Redis 同步 | expected=1000000, fpp=0.01 |
| 三级缓存 | Caffeine L1 + Redis L2 + 浏览器 L3 | L1: max=2048, expire=60s |
| Resilience4j 熔断 | Redis 故障时自动降级 | sliding-window=10, failure-rate=50% |
| Tomcat 线程池调优 | max=400, min-spare=50, accept-count=100 | max-connections=10000 |
| 优雅停机 | Spring Graceful Shutdown | timeout-per-shutdown-phase=30s |

---

## 17. 项目模块结构

```
tea-mall-backend/
├── pom.xml                          # 主模块 (后端应用)
├── gateway/
│   └── pom.xml                      # API 网关模块
├── demo-service/
│   └── pom.xml                      # 示例服务模块
├── skywalking-agent/                # SkyWalking Agent 8.9.0
├── docker/
│   ├── rocketmq/                    # RocketMQ Broker 配置
│   ├── nacos/                       # Nacos 初始化配置
│   ├── mysql/                       # MySQL 初始化脚本
│   ├── prometheus/                  # Prometheus 配置
│   └── canal/                       # Canal CDC 配置
├── Dockerfile                       # 多阶段构建
├── docker-compose.yml               # 开发环境编排
├── docker-compose.prodlike.yml      # 类生产环境编排
├── dev.sh                           # 开发环境启动脚本
└── .github/workflows/ci.yml         # CI/CD 流水线
```

---

*台账结束。本文档基于 pom.xml、Dockerfile、docker-compose*.yml、application*.yaml、bootstrap*.yaml、TECH_STACK.md、.github/workflows/ci.yml、skywalking-agent/ 等文件自动生成。*
