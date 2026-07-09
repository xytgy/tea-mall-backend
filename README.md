# CloudTea 茶商城后端

一个功能完整的茶叶电商后端系统，覆盖商品交易、商家管理、社交互动、即时通讯和秒杀活动等核心电商场景。

## 技术栈

语言: Java 17 / 框架: Spring Boot 3.3.4, Spring Cloud Alibaba / ORM: MyBatis-Plus, Flyway
数据库: MySQL 8.0（读写分离）, H2（测试）
缓存: Redis 7 + Caffeine（三级缓存）
消息队列: RocketMQ 5.3
搜索引擎: Elasticsearch 8.13（IK分词）
安全: Spring Security + JWT（双Token）
分布式锁: Redisson
熔断降级: Resilience4j
API文档: Knife4j（OpenAPI 3）
部署: Docker Compose

## 项目结构

src/main/java/com/xytgy/teamallbackend/
  annotation/     自定义注解
  aspect/         AOP切面
  cache/          缓存体系
  common/         公共类
  config/         配置类
  exception/      全局异常处理
  filter/         Servlet过滤器
  lock/           分布式锁
  module/         13个业务模块
    user          用户与认证
    product       商品（含ES搜索）
    shop          店铺
    cart          购物车
    order         订单与支付
    flashsale     秒杀
    teacircle     茶友圈
    chat          即时聊天
    favorite      收藏
    feedback      反馈
    support       客服工单
    upload        文件上传
    banner        轮播图管理
  mq/             RocketMQ
  ratelimit/      限流服务
  security/       安全工具
  utils/          工具类

## 技术亮点

### 三级缓存体系
Caffeine（L1, 60s/2048条）-> Redis（L2）-> 浏览器缓存（L3），热点数据预热 + 布隆过滤器防穿透 + fail-open初始化。

### 秒杀系统五层防护
验证码预热分流机器人 -> Lua脚本原子扣库存 -> RocketMQ异步下单削峰 -> Resilience4j熔断降级 -> 定时对账补偿。

### 全局限流
基于Redis Sorted Set滑动窗口 + Lua脚本，账号/IP双维度，小时级自动锁定，Redis宕机时fail-open放行。

### 认证与授权
JWT双Token（access + refresh）+ Spring Security过滤器链 + ThreadLocal用户上下文 + 三角色RBAC。

## 快速开始

```bash
# 前置条件：Java 17+, Docker, Maven
docker compose -f docker-compose.yml up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
# API文档: http://localhost:8080/doc.html
```

## 测试

```bash
./mvnw test
```

613个测试用例，覆盖缓存层、秒杀流程、MQ消费、限流服务、过滤器等核心路径。

## 部署

```bash
docker compose up -d
```

---

项目主要用于技术学习与展示。
