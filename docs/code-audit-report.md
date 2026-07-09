# Tea Mall Backend - 全量代码审计报告

**审计日期**: 2026-07-09  
**项目版本**: 0.0.1-SNAPSHOT  
**技术栈**: Spring Boot 3.3.4 / Java 17 / Spring Security / MyBatis-Plus / Redis / RocketMQ / Kafka / Elasticsearch / WebSocket  
**代码规模**: 356 个源文件，26 个测试文件  

---

## 一、安全问题清单

### Critical (CVSS 9.0-10.0)

#### C1. 开发环境硬编码数据库密码
- **文件**: `src/main/resources/application-dev.yaml:16,28,39`
- **问题**: 开发环境配置文件中硬编码了数据库密码（`123456`）、从库密码（`dev123456`）、Redis密码（`redis123456`）
- **风险**: 若代码仓库泄露（如误提交到公开仓库），攻击者可直接获取数据库访问权限
- **修复建议**: 将所有密码通过环境变量注入，移除明文默认值。使用 `.env` 文件管理本地开发密钥，并加入 `.gitignore`
- **优先级**: P0

#### C2. 开发环境硬编码 JWT 密钥
- **文件**: `src/main/resources/application-dev.yaml:66`
- **问题**: `jwt.secret: ${JWT_SECRET:dev-only-secret-for-local-development-only}` 提供了默认值，虽然标记为 "dev-only"，但长度不足 32 字节的要求仅在运行时检查
- **风险**: 如果生产环境忘记设置 `JWT_SECRET` 环境变量，将使用弱密钥，导致令牌伪造
- **修复建议**: `application.yaml` 主配置中 `jwt.secret: ${JWT_SECRET}` 无默认值（正确），但建议在启动时增加校验确保生产环境必须设置该变量
- **优先级**: P0

#### C3. `/uploads/**` 路径公开访问 - 静态文件无鉴权
- **文件**: `src/main/java/com/xytgy/teamallbackend/security/SecurityConstants.java:26`
- **问题**: `/uploads/**` 被配置为公开路径，任何未认证用户可直接访问上传的文件
- **风险**: 如果 OSS 配置不当或有敏感文件被上传，将导致信息泄露
- **修复建议**: 确保 OSS bucket 配置了适当的访问策略；考虑对敏感文件使用签名 URL
- **优先级**: P0

### High (CVSS 7.0-8.9)

#### H1. CORS 配置允许通配符来源
- **文件**: `src/main/java/com/xytgy/teamallbackend/config/security/SecurityConfig.java:86-101`
- **问题**: `CorsProperties` 默认值为 `*`，当配置为通配符时 `setAllowCredentials(false)` 虽然正确禁用了凭据，但 `allowedMethods` 和 `allowedHeaders` 均为 `*`，过于宽松
- **风险**: 在配置错误的情况下可能导致跨域请求被恶意网站利用
- **修复建议**: 生产环境必须通过 `CORS_ALLOWED_ORIGINS` 环境变量指定具体域名。建议在代码中增加启动校验，当 `allowedOrigins` 包含 `*` 且 `allowCredentials=true` 时抛出异常
- **优先级**: P1

#### H2. CSRF 防护完全禁用
- **文件**: `src/main/java/com/xytgy/teamallbackend/config/security/SecurityConfig.java:53`
- **问题**: `.csrf(AbstractHttpConfigurer::disable)` 完全关闭了 CSRF 防护
- **风险**: 虽然 REST API + JWT 方案通常不需要 CSRF，但 `/api/auth/login`、`/api/user/refresh/token` 等接口如果被浏览器环境调用，仍存在 CSRF 风险
- **修复建议**: 当前设计合理（JWT 无状态 + SameSite Cookie），但建议在文档中明确说明 CSRF 不适用的原因，并确保 Refresh Token 不通过 Cookie 传递
- **优先级**: P1（当前风险可控）

#### H3. WebSocket 连接未验证 Redis 在线状态的竞态条件
- **文件**: `src/main/java/com/xytgy/teamallbackend/config/websocket/ChatWebSocketHandler.java:97-148`
- **问题**: WebSocket 连接建立时检查 `login:user:{userId}` Redis key 是否存在，但存在 TOCTOU（Time-of-check to time-of-use）竞态：用户登出后 key 被删除，但已建立的 WebSocket 连接不会自动断开
- **风险**: 已登出用户可通过已建立的 WebSocket 连接继续发送消息
- **修复建议**: 在每次 WebSocket 消息处理时重新验证用户在线状态
- **优先级**: P1

#### H4. KafkaDemoController 无鉴权且暴露生产环境
- **文件**: `src/main/java/com/xytgy/teamallbackend/module/flashsale/controller/KafkaDemoController.java:1-35`
- **问题**: 该控制器映射到 `/api/kafka/send`，无 `@PreAuthorize` 注解，且未在 `SecurityConstants` 中排除公开路径。由于 SecurityConfig 要求 `anyRequest().authenticated()`，该接口需要认证，但无角色限制
- **风险**: 任何登录用户都可以向 Kafka 发送任意消息，可能影响消息队列稳定性
- **修复建议**: 该控制器应仅在开发 profile 下激活（使用 `@Profile("dev")`），或添加管理员角色限制
- **优先级**: P1

#### H5. Dev 环境默认开启文档暴露
- **文件**: `src/main/resources/application-dev.yaml:54-58`
- **问题**: 开发环境默认开启 Knife4j (`enable: true`) 和文档访问 (`docs.enabled: true`)
- **风险**: 开发环境的 API 文档可能暴露内部接口细节
- **修复建议**: 文档访问仅限 localhost，确保开发环境不暴露到公网
- **优先级**: P1

### Medium (CVSS 4.0-6.9)

#### M1. XSS 过滤器基于黑名单不够全面
- **文件**: `src/main/java/com/xytgy/teamallbackend/config/XssFilter.java:26-53`
- **问题**: XSS 防护使用正则黑名单过滤 `<script>`、`on*` 事件、`javascript:`、`vbscript:`、`expression()`，但存在绕过可能（如 `<img src=x onerror=alert(1)>` 中的 `onerror` 会被匹配，但 `<svg onload=alert(1)>` 中的 `onload` 也会被匹配——当前实现覆盖了 `on\w+` 模式，实际上已包含 `onload`。但仍有如 `data:text/html` 等新协议绕过的可能）
- **风险**: 特定构造的 payload 可能绕过过滤
- **修复建议**: 考虑使用 OWASP Java Encoder 进行输出编码，或使用 Jsoup 库进行 HTML 清理（白名单方式）
- **优先级**: P2

#### M2. 限流器未排除内部健康检查端点
- **文件**: `src/main/java/com/xytgy/teamallbackend/filter/GlobalRateLimitFilter.java:96-103`
- **问题**: 限流器仅对 `/api/` 路径生效，但 `/actuator/health` 等端点未在 PUBLIC_PATHS 中（`/actuator/**` 已在公开路径中），所以不会被限流（正确）。但如果监控系统频繁调用 `/api/` 路径，可能触发限流
- **风险**: 监控系统可能被误限流
- **修复建议**: 考虑为监控系统 IP 添加白名单
- **优先级**: P2

#### M3. 文件上传缺少文件名路径穿越防护
- **文件**: `src/main/java/com/xytgy/teamallbackend/utils/AliyunOSSUtils.java:32-39`
- **问题**: 上传文件名使用 `UUID + 原始扩展名`，扩展名从原始文件名提取。虽然 UUID 保证了文件名不可预测，但原始文件名中的扩展名未做白名单校验
- **风险**: 虽然 Content-Type 和魔术字已校验，但扩展名可能被利用（如 `.html` 扩展名的文件被上传到 OSS）
- **修复建议**: 扩展名也应限制为白名单（`.jpg`, `.png`, `.gif`, `.webp`）
- **优先级**: P2

#### M4. 密码修改接口缺失
- **文件**: `src/main/java/com/xytgy/teamallbackend/module/user/controller/UserController.java`
- **问题**: 用户模块缺少修改密码接口，用户无法自行修改密码
- **风险**: 用户密码泄露后无法及时修改
- **修复建议**: 添加修改密码接口，并在修改后使所有 RefreshToken 失效
- **优先级**: P2

#### M5. UserMapper.xml 查询结果包含密码字段
- **文件**: `src/main/resources/mapper/user/UserMapper.xml:10,23-27`
- **问题**: `Base_Column_List` 包含 `password` 字段，所有使用该 SQL 片段的查询都会返回密码
- **风险**: 如果 VO 转换不当，密码可能泄露到前端
- **修复建议**: 创建不含密码的列列表用于查询展示数据
- **优先级**: P2

### Low (CVSS 0.1-3.9)

#### L1. 日志中可能泄露 WebSocket sessionId
- **文件**: `src/main/java/com/xytgy/teamallbackend/config/websocket/ChatWebSocketHandler.java:119`
- **问题**: `log.warn("WebSocket token 是违法的 sessionId={}", session.getId())` 在 warn 级别打印 sessionId
- **风险**: 日志中包含 sessionId 可能被用于会话固定攻击
- **修复建议**: 降级为 debug 级别或脱敏处理
- **优先级**: P3

#### L2. DigestUtils 使用 MD5 算法
- **文件**: `src/main/java/com/xytgy/teamallbackend/utils/DigestUtils.java:21`
- **问题**: 使用 MD5 作为摘要算法，MD5 已被证明不安全
- **风险**: 如果用于安全目的（如签名），存在碰撞攻击风险
- **修复建议**: 确认 MD5 仅用于非安全目的（如缓存键），如需安全摘要应使用 SHA-256
- **优先级**: P3

#### L3. 重复的 Actuator 依赖
- **文件**: `pom.xml:224-233`
- **问题**: `spring-boot-starter-actuator` 依赖被重复声明了两次
- **风险**: 无安全风险，但可能导致构建混淆
- **修复建议**: 移除重复依赖
- **优先级**: P3

---

## 二、合规性问题清单

### 代码规范

#### CF1. 包结构合理性 ✅
- 项目采用模块化包结构：`module/{业务域}/controller/service/mapper/dto/vo/entity`
- 公共组件放置在 `common/`、`config/`、`utils/`、`security/` 等包下
- **评价**: 结构清晰，符合领域驱动设计思想

#### CF2. 命名规范 ✅
- 控制器以 `Controller` 结尾，服务以 `Service`/`ServiceImpl` 结尾
- DTO/VO 命名清晰：`*Request`、`*VO`、`*Response`
- 常量命名规范：`private static final` 使用 UPPER_SNAKE_CASE
- **评价**: 命名规范良好

#### CF3. 异常处理统一性 ✅
- 全局异常处理器 `GlobalExceptionHandler` 覆盖了：
  - `ServiceException`（业务异常）
  - `LockAcquisitionException`（分布式锁异常）
  - `MethodArgumentNotValidException`（参数校验）
  - `MissingServletRequestParameterException`（缺少参数）
  - `HttpRequestMethodNotSupportedException`（方法不支持）
  - `MaxUploadSizeExceededException`（文件大小超限）
  - `Exception`（兜底异常）
- **评价**: 异常处理覆盖全面，返回格式统一

#### CF4. 注释覆盖率
- **问题**: 大部分类和方法缺少 Javadoc 注释
- **评价**: 关键安全类（如 `JwtAuthenticationFilter`、`SecurityConfig`）有良好注释，但 Service 层和 DTO 类缺少文档
- **建议**: 为所有公开 API 方法添加 `@Operation` 注解的 `description`，为 Service 方法添加 Javadoc

#### CF5. 日志规范 ✅
- 使用 SLF4J + Lombok `@Slf4j`
- 敏感信息处理良好：数据库连接日志脱敏（`password=***`）
- 审计日志：管理员操作有 `[AUDIT]` 前缀标记
- **评价**: 日志规范良好，但建议统一日志格式

### 配置管理

#### CF6. 敏感配置外部化 ✅
- JWT 密钥通过 `${JWT_SECRET}` 环境变量注入
- 数据库密码通过 `${DB_PASSWORD}` 环境变量注入
- Redis 密码通过 `${REDIS_PASSWORD}` 环境变量注入
- OSS 密钥通过 `${ALIYUN_OSS_ACCESS_KEY_ID/SECRET}` 环境变量注入
- 支付宝配置通过 `${ALIPAY_*}` 环境变量注入
- **评价**: 敏感配置外部化做得很好，但开发环境仍有硬编码默认值（见 C1）

#### CF7. 配置文件分离 ✅
- `application.yaml`（主配置）
- `application-dev.yaml`（开发环境）
- `application-prod.yaml`（生产环境）
- `bootstrap.yaml`（Nacos 配置中心）
- **评价**: 配置分离合理

#### CF8. Actuator 暴露控制 ✅
- 仅暴露 `health` 端点，且 `show-details: when-authorized`
- 生产环境禁用 Knife4j 和 API 文档
- **评价**: 监控端点暴露控制良好

---

## 三、架构风险点清单

### 分层架构

#### A1. Controller/Service/Mapper 边界清晰度 ✅
- Controller 层仅做参数接收和响应封装
- Service 层包含核心业务逻辑
- Mapper 层使用 MyBatis-Plus 的 LambdaQueryWrapper，无 XML 中的复杂动态 SQL
- **评价**: 分层边界清晰，符合单一职责原则

#### A2. 模块间耦合度
- **问题**: `PaymentController` 直接依赖 `AlipayClient`、`AlipayConfig`、`DistributedLock`、`PaymentEventPublisher`、`StringRedisTemplate`，共 7 个依赖
- **风险**: 支付模块过于耦合，修改支付宝 SDK 版本可能影响多个组件
- **建议**: 引入支付渠道抽象层，将支付宝实现封装为策略模式

#### A3. 循环依赖检测 ✅
- 通过代码审查未发现明显的循环依赖
- 使用 `@RequiredArgsConstructor` 构造器注入，Spring 会在启动时检测循环依赖
- **评价**: 无循环依赖风险

#### A4. Service 层臃肿度
- **问题**: `UserServiceImpl` 有 556 行，承担了登录、注册、用户管理、头像上传、统计等职责
- **风险**: 单一职责原则被违反，修改用户头像逻辑可能影响登录流程
- **建议**: 将头像上传、用户统计等独立为单独的 Service

#### A5. 配置类合理性 ✅
- 配置类职责清晰：
  - `SecurityConfig` - 安全配置
  - `DynamicDataSourceConfig` - 数据源配置
  - `WebSocketConfig` - WebSocket 配置
  - `AsyncThreadPoolConfig` - 线程池配置
  - `RedissonConfig` - Redisson 配置
- **评价**: 配置类划分合理，无过度配置

### 测试覆盖率

#### A6. 测试覆盖严重不足
- **源文件**: 356 个
- **测试文件**: 26 个
- **估算覆盖率**: < 10%
- **问题**: 
  - 缺少 Controller 层的集成测试
  - 缺少 Security 相关的测试（JWT 验证、权限控制）
  - 缺少支付流程的端到端测试
  - 缺少异常场景的测试
- **建议**: 
  - 优先为支付、订单、秒杀等核心链路编写测试
  - 为 Security 配置编写集成测试
  - 目标覆盖率至少达到 40%

### 其他架构风险

#### A7. Redis 故障降级策略
- **问题**: 多处 Redis 操作使用 `try-catch(Exception ignored)` 降级
- **风险**: Redis 故障时，认证、缓存、限流等功能可能失效
- **建议**: 引入 Resilience4j 熔断器保护 Redis 操作（已部分实现）

#### A8. RocketMQ 消息可靠性
- **问题**: `MqProducer` 在发送失败时写入本地重试表，但 `NoOpMqProducer` 直接丢弃消息
- **风险**: RocketMQ 不可用时消息可能丢失
- **建议**: 确保 `NoOpMqProducer` 仅在开发环境使用

#### A9. 分布式锁 Watch Dog 超时
- **问题**: `DistributedLock` 默认使用 Watch Dog 自动续期，但未设置最大持有时间
- **风险**: 如果业务逻辑异常导致锁未释放，Watch Dog 会无限续期
- **建议**: 设置 `lockWatchdogTimeout` 最大值

---

## 四、修复优先级建议

| 优先级 | 编号 | 问题描述 | 影响范围 | 修复难度 |
|--------|------|----------|----------|----------|
| **P0** | C1 | 开发环境硬编码数据库密码 | 数据安全 | 低 |
| **P0** | C2 | JWT 密钥默认值风险 | 认证安全 | 低 |
| **P0** | C3 | `/uploads/**` 公开访问 | 文件安全 | 低 |
| **P1** | H1 | CORS 通配符配置 | 跨域安全 | 低 |
| **P1** | H4 | KafkaDemoController 无角色限制 | 消息队列安全 | 低 |
| **P1** | H3 | WebSocket 登出后连接未断开 | 会话安全 | 中 |
| **P2** | M1 | XSS 黑名单过滤不全面 | 输入安全 | 中 |
| **P2** | M3 | 文件上传扩展名未白名单 | 文件安全 | 低 |
| **P2** | M4 | 缺少密码修改接口 | 用户安全 | 中 |
| **P2** | M5 | UserMapper 查询包含密码字段 | 数据安全 | 低 |
| **P3** | L1 | WebSocket sessionId 日志泄露 | 信息泄露 | 低 |
| **P3** | L2 | MD5 算法使用 | 加密安全 | 低 |
| **P3** | L3 | 重复 Actuator 依赖 | 构建规范 | 低 |
| **P1** | A6 | 测试覆盖率不足 | 质量保障 | 高 |
| **P2** | A2 | PaymentController 耦合度高 | 可维护性 | 高 |
| **P2** | A4 | UserServiceImpl 过于臃肿 | 可维护性 | 中 |

---

## 五、安全加固检查清单

### 已实现的安全措施 ✅
1. ✅ JWT 认证 + Redis 在线状态校验
2. ✅ Spring Security 方法级权限控制（`@PreAuthorize`）
3. ✅ XSS 过滤器（黑名单方式）
4. ✅ HSTS + Content-Type-Options + X-Frame-Options + CSP 安全头
5. ✅ 全局 API 限流（令牌桶算法）
6. ✅ 登录接口账号级 + IP 级限速
7. ✅ 文件上传 Content-Type 校验 + 魔术字校验
8. ✅ 密码 BCrypt 加密存储
9. ✅ 敏感配置环境变量外部化
10. ✅ 审计日志（管理员操作）
11. ✅ 支付回调幂等性保护（Redis SETNX）
12. ✅ 分布式锁（Redisson）
13. ✅ 优雅停机
14. ✅ 可信代理 IP 校验（防止 X-Forwarded-For 伪造）

### 待改进项 ⚠️
1. ⚠️ XSS 过滤器升级为白名单方式（OWASP Encoder / Jsoup）
2. ⚠️ 添加密码修改接口
3. ⚠️ 测试覆盖率提升至 40%+
4. ⚠️ WebSocket 消息处理时重新验证在线状态
5. ⚠️ KafkaDemoController 添加环境隔离
6. ⚠️ UserMapper 查询分离密码字段

---

## 六、总结

### 整体评价
该项目在安全架构方面做得**较好**，采用了 Spring Security + JWT 的标准认证方案，实现了全局限流、支付幂等、分布式锁等关键安全机制。配置管理方面，生产环境的敏感信息通过环境变量注入，符合安全最佳实践。

### 主要风险
1. **开发环境安全**: 硬编码密码和弱 JWT 密钥是最大的安全隐患，需要立即修复
2. **测试覆盖不足**: < 10% 的测试覆盖率是最大的质量风险
3. **WebSocket 安全**: 登出后连接未断开是一个实际可被利用的安全漏洞

### 建议行动
1. **立即修复（P0）**: 移除所有硬编码密码，确保 JWT 密钥强制外部化
2. **本周修复（P1）**: 修复 CORS 配置、KafkaDemoController 权限、WebSocket 登出问题
3. **本月完成（P2）**: 升级 XSS 防护、添加密码修改接口、提升测试覆盖率
4. **持续改进（P3）**: 代码注释完善、日志脱敏、依赖版本更新

---

*审计完成时间: 2026-07-09 10:00*  
*审计工具: 人工代码审查 + 静态分析*  
*审计范围: 全部源代码 (356 文件) + 配置文件 + 依赖项*
