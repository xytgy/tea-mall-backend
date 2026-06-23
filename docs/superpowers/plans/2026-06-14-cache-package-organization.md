# Cache Package Organization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按 BloomFilter、多级缓存和浏览器缓存三个职责整理 `cache` 包。

**Architecture:** 保持所有类和公开接口不变，仅调整 Java 包路径和引用。测试类跟随被测模块移动，最后通过全量测试验证没有行为变化。

**Tech Stack:** Java 17、Spring Boot、JUnit 5、Maven

---

### Task 1: 移动生产代码

**Files:**
- Move: `src/main/java/com/xytgy/teamallbackend/cache/BloomFilter*.java`
- Move: `src/main/java/com/xytgy/teamallbackend/cache/event/*.java`
- Move: `src/main/java/com/xytgy/teamallbackend/cache/LastModifiedProvider.java`
- Move: `src/main/java/com/xytgy/teamallbackend/cache/NoLastModifiedProvider.java`
- Move: `src/main/java/com/xytgy/teamallbackend/cache/{CacheKeyCleaner,CacheMetrics,HotCacheEntry,HotCacheService,MultiLevelCacheService}.java`

- [ ] **Step 1:** 将 BloomFilter 文件移动到 `cache/bloom`，事件移动到 `cache/bloom/event`。
- [ ] **Step 2:** 将浏览器缓存接口移动到 `cache/browser`。
- [ ] **Step 3:** 将多级缓存文件移动到 `cache/multilevel`。
- [ ] **Step 4:** 更新各文件 `package` 和内部 import。

### Task 2: 更新调用方和测试

**Files:**
- Modify: `src/main/java/com/xytgy/teamallbackend/utils/RedisUtils.java`
- Modify: `src/main/java/com/xytgy/teamallbackend/aspect/BrowserCacheAspect.java`
- Modify: `src/main/java/com/xytgy/teamallbackend/annotation/BrowserCache.java`
- Modify: `src/main/java/com/xytgy/teamallbackend/module/product/cache/ProductLastModifiedProvider.java`
- Modify: `src/main/java/com/xytgy/teamallbackend/module/product/service/impl/ProductServiceImpl.java`
- Modify: `src/main/java/com/xytgy/teamallbackend/module/user/service/impl/UserServiceImpl.java`
- Move: `src/test/java/com/xytgy/teamallbackend/cache/BloomFilter*.java`
- Modify: existing test imports

- [ ] **Step 1:** 更新全部生产代码 import。
- [ ] **Step 2:** 移动 BloomFilter 测试并更新测试 import。
- [ ] **Step 3:** 使用 `rg` 确认不存在旧包引用。

### Task 3: 验证

- [ ] **Step 1:** 运行 `./mvnw test`。
- [ ] **Step 2:** 预期结果为全部测试通过且无编译错误。
