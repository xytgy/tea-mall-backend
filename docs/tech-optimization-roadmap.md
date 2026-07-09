# Tea-Mall-Backend 技术栈优化 Roadmap 与项目迭代规划

> 整合来源：技术栈全景台账 + 企业级对标分析 + 全量代码审计  
> 生成日期：2026-07-09  
> 项目路径：`/Users/xytgy/Downloads/software/tea-mall-backend`

---

## 一、全局风险概览

### 1.1 风险分布统计

| 级别 | 数量 | 来源分布 |
|------|------|---------|
| **P0 致命** | 6 | 技术架构 3 + 代码安全 3 |
| **P1 高** | 17 | 技术架构 12 + 代码安全 5 |
| **P2 中** | 15 | 技术架构 10 + 代码安全 5 |
| **P3 低** | 8 | 技术架构 5 + 代码安全 3 |
| **合计** | **46** | — |

### 1.2 三大核心风险域

| 风险域 | P0 | P1 | 核心问题 |
|--------|-----|-----|---------|
| **基础设施高可用** | 2 | 4 | Redis 单实例、无 CI/CD、无日志聚合、ES 单节点、无数据库备份 |
| **安全合规** | 3 | 5 | 硬编码密码/密钥、CORS 通配符、WebSocket 竞态、无审计日志、无依赖漏洞扫描 |
| **质量保障** | 1 | 8 | 测试覆盖率 <10%、无灰度发布、JVM 内存偏小、Zipkin 残留需清理、网关未集成 |

---

## 二、优化路线图（四阶段）

### Phase 1：紧急止血（第 1-2 周）

> 目标：消除致命风险，确保系统基本可运行、可排查问题

| 序号 | 任务 | 风险编号 | 预计工时 | 负责模块 |
|------|------|---------|---------|---------|
| 1.1 | **移除所有硬编码密码和 JWT 弱默认值**，`application-dev.yaml` 中密码改用 `.env` 文件注入，`.env` 加入 `.gitignore` | C1, C2 | 0.5 天 | 配置文件 |
| 1.2 | **修复 `/uploads/**` 公开访问路径**，确认 OSS bucket 访问策略，敏感文件改用签名 URL | C3 | 0.5 天 | SecurityConfig |
| 1.3 | **修复 CORS 通配符配置**，生产环境强制通过环境变量指定域名，增加启动校验 | H1 | 0.5 天 | SecurityConfig |
| 1.4 | **KafkaDemoController 添加 `@Profile("dev")`**，防止生产环境暴露 | H4 | 0.5 天 | Controller |
| 1.5 | **修复 RocketMQ 依赖版本冲突**，运行 `mvn dependency:tree` 确认冲突，统一版本 | P1-01 | 0.5 天 | pom.xml |
| 1.6 | **调整 JVM 参数**：后端 Xmx→2g，ES Xmx→2g，Nacos Xmx→512m | P1-03 | 0.5 天 | Dockerfile / compose |
| 1.7 | **修复 Lombok 版本不一致**，统一为 1.18.36 | P3-04 | 0.1 天 | pom.xml |
| 1.8 | **移除重复的 actuator 依赖** | P3-03 | 0.1 天 | pom.xml |
| 1.9 | **UserMapper.xml `Base_Column_List` 移除 password 字段** | M5 | 0.5 天 | Mapper XML |
| 1.10 | **WebSocket 消息处理时重新验证用户在线状态** | H3 | 1 天 | ChatWebSocketHandler |

**Phase 1 交付物**：安全加固后的代码 + 修正后的 pom.xml + 调优后的 JVM 参数  
**预计耗时**：4-5 个工作日

---

### Phase 2：可观测性与质量基座（第 3-6 周）

> 目标：补齐可观测性三支柱 + 建立质量保障体系

| 序号 | 任务 | 风险编号 | 预计工时 | 技术方案 |
|------|------|---------|---------|---------|
| 2.1 | **搭建 CI/CD 流水线** | P0-02 | 3 天 | GitHub Actions：lint → test → build → docker push → tag release |
| 2.2 | **引入集中式日志聚合** | P0-03 | 5 天 | **方案 A（推荐）**：Loki + Promtail + Grafana（轻量，资源消耗低）<br>**方案 B**：ELK Stack（功能全但资源重） |
| 2.3 | **移除 Zipkin，统一使用 SkyWalking** | P1-02 | 1 天 | 删除 docker-compose.prodlike.yml 中 Zipkin 服务，完善 SkyWalking Agent 配置（当前已有 SkyWalking 8.9.0，Zipkin 已弃用但仍部署） |
| 2.4 | **Actuator 暴露 Prometheus metrics** | P1-11 | 1 天 | 配置 `management.endpoints.web.exposure.include=health,info,prometheus`，Grafana 导入 Spring Boot Dashboard |
| 2.5 | **日志注入 TraceId/SpanId** | P1-12 | 1 天 | SkyWalking Agent 自动注入，Logback pattern 添加 `%X{tid}` |
| 2.6 | **引入 JaCoCo 覆盖率工具** | P1-05 | 1 天 | pom.xml 添加 JaCoCo plugin，CI 中生成覆盖率报告 |
| 2.7 | **补充核心模块单元测试**（支付、订单、秒杀） | P1-05 | 5-8 天 | 目标覆盖率从 <10% 提升至 40% |
| 2.8 | **实现安全审计日志** | P1-06 | 3 天 | AOP 切面 + 数据库表 `audit_log`，记录关键操作 |
| 2.9 | **文件上传扩展名白名单** | M3 | 0.5 天 | AliyunOSSUtils 中增加扩展名白名单校验 |
| 2.10 | **添加密码修改接口** | M4 | 2 天 | UserController 新增 changePassword 接口，修改后使所有 RefreshToken 失效 |
| 2.11 | **XSS 过滤器升级为白名单方式** | M1 | 2 天 | 引入 Jsoup 库，替换正则黑名单为 HTML 白名单清理 |
| 2.12 | **Gateway 模块集成到 Docker Compose** | P1-04 | 2-3 天 | 在 prodlike compose 中添加 gateway 服务，启用 Sentinel |

**Phase 2 交付物**：CI/CD 流水线 + Grafana 监控大盘 + SkyWalking 链路追踪完善 + 审计日志 + 核心测试  
**预计耗时**：25-35 个工作日

---

### Phase 3：高可用架构升级（第 7-12 周）

> 目标：消除单点故障，实现基础设施高可用

| 序号 | 任务 | 风险编号 | 预计工时 | 技术方案 |
|------|------|---------|---------|---------|
| 3.1 | **Redis 哨兵模式部署** | P0-01 | 2-3 天 | 1 主 + 2 从 + 3 哨兵，Docker Compose 编排，Redisson 配置哨兵模式 |
| 3.2 | **ES 多节点集群** | P1-08 | 2-3 天 | 3 节点集群（1 master + 2 data），移除 `single-node` 配置 |
| 3.3 | **数据库自动备份策略** | P1-09 | 1-2 天 | mysqldump 定时备份 + 阿里云 RDS 自动备份（如已迁云） |
| 3.4 | **jjwt 升级至 0.12.x** | P2-01 | 1-2 天 | API 变更适配，密钥长度校验增强 |
| 3.5 | **灰度发布能力** | P2-05 | 1-2 周 | Nacos metadata 标签 + Gateway 路由规则，按用户 ID / 流量比例灰度 |
| 3.6 | **秒杀场景压测** | P2-06 | 1 周 | JMeter/Gatling 压测脚本，建立性能基线，识别瓶颈 |
| 3.7 | **Redis Lettuce 连接池扩容** | P2-03 | 0.5 天 | max-active 从 32 提升至 64-128 |
| 3.8 | **HikariCP 指标接入 Prometheus** | P2-08 | 1 天 | MeterRegistryCustomizer 配置连接池指标 |
| 3.9 | **Sentinel Dashboard 部署** | P2-09 | 1 天 | Docker 部署 Sentinel Dashboard，动态管理流控规则 |
| 3.10 | **Nacos Config 启用 + 配置加密** | P1-07 | 3-5 天 | 启用 Nacos 配置中心，敏感配置加密存储 |
| 3.11 | **引入密钥轮换机制** | P2-07 | 2-3 天 | JWT 密钥定期轮换，Refresh Token 失效策略 |
| 3.12 | **PaymentController 重构** | A2 | 3 天 | 引入支付渠道抽象层（Strategy 模式），解耦支付宝 SDK |
| 3.13 | **UserServiceImpl 拆分** | A4 | 2 天 | 拆分为 UserService、UserAvatarService、UserStatsService |

**Phase 3 交付物**：Redis 高可用集群 + ES 集群 + 自动备份 + 灰度发布 + 性能基线  
**预计耗时**：20-30 个工作日

---

### Phase 4：云原生演进（第 13-20 周）

> 目标：面向未来架构，支持弹性伸缩和自动化运维

| 序号 | 任务 | 风险编号 | 预计工时 | 技术方案 |
|------|------|---------|---------|---------|
| 4.1 | **Docker Compose 迁移至 Kubernetes** | P1-10 | 2-4 周 | K3s（开发） / 阿里云 ACK（生产），Helm Charts 编排 |
| 4.2 | **GitOps 部署流程** | P0-02 | 1 周 | ArgoCD + GitOps 工作流，声明式部署 |
| 4.3 | **容器资源限制** | — | 2 天 | compose/k8s 中配置 CPU/Memory limits，防止资源争抢 |
| 4.4 | **依赖漏洞自动扫描** | P1 | 2 天 | OWASP Dependency Check 或 Snyk 集成到 CI |
| 4.5 | **契约测试** | P2 | 1 周 | Spring Cloud Contract，确保接口兼容性 |
| 4.6 | **接口幂等性框架** | P2 | 3 天 | 统一幂等注解 + Redis 实现，替代分散的锁逻辑 |
| 4.7 | **分布式配置加密** | P2 | 3 天 | Nacos 配置加密或 HashiCorp Vault |
| 4.8 | **数据脱敏增强** | P2 | 2 天 | 脱敏注解 + AOP，手机号/身份证/银行卡自动脱敏 |

**Phase 4 交付物**：Kubernetes 部署 + GitOps 流水线 + 安全扫描 + 契约测试  
**预计耗时**：25-35 个工作日

---

## 三、推荐技术选型方案

基于项目当前阶段（单体+微服务混合架构，团队规模中小），**推荐方案 A：渐进式升级**。

### 推荐方案：渐进式升级

| 组件 | 当前 | 目标 | 迁移成本 |
|------|------|------|---------|
| 日志 | 本地 Slf4j | Loki + Promtail + Grafana | 低（3-5 天） |
| 追踪 | Zipkin 2.24（已弃用） | 移除 Zipkin，统一使用 SkyWalking 8.9.0 | 低（1 天） |
| 监控 | Micrometer（未暴露） | Prometheus + Grafana + AlertManager | 低（2-3 天） |
| 网关 | 未启用 | 启用 Gateway + Sentinel | 低（2-3 天） |
| 密钥 | 环境变量 | Nacos 配置加密 + 轮换机制 | 中（3-5 天） |
| 部署 | Docker Compose | Kubernetes（K3s 开发 / 云 K8s 生产） | 高（2-4 周） |
| CI/CD | 无 | GitHub Actions + ArgoCD（GitOps） | 中（1 周） |

**总迁移成本**：约 8-12 周（分 4 阶段执行）  
**优点**：渐进式推进，风险低，团队学习成本低  
**缺点**：架构天花板受限于 Spring Cloud Alibaba 生态

---

## 四、关键里程碑

```
Week 1-2   ── Phase 1: 紧急止血 ────────────────────────── 安全漏洞清零
    │
Week 3-6   ── Phase 2: 可观测性 + 质量基座 ─────────────── 可排查、可测试
    │
Week 7-12  ── Phase 3: 高可用架构升级 ──────────────────── 无单点故障
    │
Week 13-20 ── Phase 4: 云原生演进 ──────────────────────── 弹性伸缩
```

---

## 五、风险与依赖

| 风险项 | 影响 | 缓解措施 |
|--------|------|---------|
| Redis 哨兵切换期间服务中断 | 秒杀/缓存短暂不可用 | 选择低峰期切换，提前通知 |
| 移除 Zipkin 时误删 SkyWalking 相关配置 | 链路追踪中断 | 仅删除 Zipkin 服务和 Brave 依赖，保留 SkyWalking Agent |
| 测试覆盖率提升耗时超预期 | Phase 2 延期 | 优先覆盖支付/订单/秒杀核心链路 |
| K8s 运维能力不足 | Phase 4 执行困难 | 先用 K3s 开发环境练兵，生产使用云 K8s |
| 团队对新技术栈不熟悉 | 学习曲线陡峭 | 每阶段安排技术分享，渐进式引入 |

---

## 六、附录：完整问题清单索引

| 文档 | 路径 | 内容 |
|------|------|------|
| 技术栈台账 | `docs/tech-stack-inventory.md` | 14 维度完整技术栈清单，40+ 依赖版本 |
| 对标分析报告 | `docs/tech-stack-benchmark-analysis.md` | 3 个 P0 + 12 个 P1 风险，3 套替代方案 |
| 代码审计报告 | `docs/code-audit-report.md` | 3 个 Critical + 5 个 High 安全问题，架构风险点 |
| 本文档 | `docs/tech-optimization-roadmap.md` | 四阶段优化路线图与迭代规划 |

---

*Roadmap 生成完毕。建议每季度重新评估技术栈状态，根据业务发展调整优先级。*
