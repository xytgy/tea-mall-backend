# 离线测试基线设计

## 目标

让开发者从一个全新的项目副本中，直接执行：

```bash
./mvnw test
```

该命令必须成功完成，并且不依赖 MySQL、Redis、RocketMQ、
Elasticsearch、Nacos 或其他外部服务。

## 范围

本次改动仅处理构建与测试基线：

- 补齐现有 `mvnw` 启动脚本所需的 Maven Wrapper 配置及相关文件。
- 将 Spring 的 `test` Profile 与外部基础设施隔离。
- 保留轻量级 Spring 上下文测试，用于验证必要的基础配置和 Bean 装配。
- 保留并正常执行现有单元测试。
- 配置兼容当前 JDK 环境的 Mockito Mock Maker，避免依赖 JVM 自附加能力。

本次不会修改生产业务行为，也不会重构订单、支付、秒杀或其他业务模块。

## 测试架构

### Maven Wrapper

项目中将包含一套完整的 Maven Wrapper，版本需兼容 Java 17 和当前使用的
Spring Boot 3.3.4。开发者无需在本机全局安装 Maven。

### 单元测试

`RedisUtilsTest`、`BrowserCacheAspectTest` 等测试继续使用 JUnit 5 和
Mockito。Mockito 将使用基于子类的 Mock Maker，使测试 JVM 无需通过
Byte Buddy 动态附加代理。

### 轻量 Spring 上下文

`test` Profile 将关闭或排除会连接外部系统、或者依赖生产连接参数的基础设施。
上下文测试只加载验证基础装配所需的应用组件。面向数据库、Redis、MQ、ES 和
Nacos 的依赖将在测试侧被替换或排除。

`BloomFilterManagerTest` 将改为聚焦的单元测试，或使用测试替身提供依赖，
避免仅为测试内存中的布隆过滤器而启动完整生产应用上下文。

## 配置边界

外部服务隔离配置应放在测试资源和测试类中。生产 YAML 与生产 Java 业务行为
保持不变。

如果遇到只有修改生产构建配置才能解决的兼容性问题，需要在实施前单独说明，
不能顺手扩大改动范围。

不能通过以下方式让测试表面通过：

- 跳过测试；
- 禁用 Maven Surefire；
- 将失败测试标记为忽略；
- 要求开发者预先启动外部服务。

## 失败处理

- Maven Wrapper 文件缺失时，补齐文件，不回退到全局 `mvn`。
- 外部服务未运行时，不应出现连接重试或长时间等待。
- 某个 Spring Bean 无法在轻量上下文中装配时，优先在测试侧替换或排除。
- Mockito 因 JVM 代理附加失败时，通过测试配置解决，不要求开发者修改本机 JVM。

## 验收标准

在项目根目录中，确保相关外部服务均未启动，然后执行：

```bash
./mvnw test
```

命令必须以退出码 `0` 完成。

该命令必须实际运行所有已启用测试，不能绕过测试，也不能依赖全局 Maven。

## 不在本次范围内

- 修改 GitHub Actions
- 设置测试覆盖率门槛
- 新增订单、支付或秒杀业务测试
- 重构生产架构
- Docker 集成测试
- 基于 Testcontainers 或 H2 的完整应用集成测试
