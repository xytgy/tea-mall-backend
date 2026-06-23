# Cache Package Reorganization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganize the overloaded `cache.multilevel` package into responsibility-based cache subpackages while keeping business-facing cache APIs stable.

**Architecture:** This migration is structural rather than behavioral. We will create new cache subpackages, move low-coupling support classes first, then move core cache services, then migrate invalidation and facade classes last so dependency-heavy code is touched only after the new package boundaries are in place.

**Tech Stack:** Spring Boot, Java 17, Maven, JUnit 5, Mockito

---

## File Structure

### New directories to create

- `src/main/java/com/xytgy/teamallbackend/cache/facade`
- `src/main/java/com/xytgy/teamallbackend/cache/hot`
- `src/main/java/com/xytgy/teamallbackend/cache/standard`
- `src/main/java/com/xytgy/teamallbackend/cache/invalidation`
- `src/main/java/com/xytgy/teamallbackend/cache/key`
- `src/main/java/com/xytgy/teamallbackend/cache/local`
- `src/main/java/com/xytgy/teamallbackend/cache/metrics`

### Existing production files to move and update

- Move to `cache.metrics`:
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/CacheMetrics.java`
- Move to `cache.key`:
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/RedisGlobPattern.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/VersionedCacheKeyService.java`
- Move to `cache.hot`:
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheOptions.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheReadResult.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheRecord.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheEntry.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheCodec.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheCoordinator.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheLockService.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheService.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheStore.java`
- Move to `cache.standard`:
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/MultiLevelCacheService.java`
- Move to `cache.local`:
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/LocalCacheInvalidator.java`
- Move to `cache.invalidation`:
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/CacheInvalidationEvent.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/CacheInvalidationEventHandler.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/CacheInvalidationEventPublisher.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/CacheInvalidationMessage.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/CacheInvalidationRetryService.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/CacheInvalidationSyncService.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/CacheKeyCleaner.java`
- Move to `cache.facade`:
  - `src/main/java/com/xytgy/teamallbackend/utils/RedisUtils.java`

### Existing test files to move/update

- `src/test/java/com/xytgy/teamallbackend/cache/multilevel/CacheInvalidationEventHandlerTest.java`
- `src/test/java/com/xytgy/teamallbackend/cache/multilevel/CacheInvalidationSyncServiceTest.java`
- `src/test/java/com/xytgy/teamallbackend/cache/multilevel/CacheKeyCleanerTest.java`
- `src/test/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheLockServiceTest.java`
- `src/test/java/com/xytgy/teamallbackend/cache/multilevel/RedisGlobPatternTest.java`
- `src/test/java/com/xytgy/teamallbackend/cache/multilevel/VersionedCacheKeyServiceTest.java`
- `src/test/java/com/xytgy/teamallbackend/utils/RedisUtilsTest.java`

## Task 1: Create target packages and move low-coupling support classes

**Files:**
- Create: `src/main/java/com/xytgy/teamallbackend/cache/metrics/`
- Create: `src/main/java/com/xytgy/teamallbackend/cache/key/`
- Create: `src/main/java/com/xytgy/teamallbackend/cache/hot/`
- Move/Modify:
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/CacheMetrics.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/RedisGlobPattern.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/VersionedCacheKeyService.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheOptions.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheReadResult.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheRecord.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheEntry.java`
- Test:
  - `src/test/java/com/xytgy/teamallbackend/cache/multilevel/RedisGlobPatternTest.java`
  - `src/test/java/com/xytgy/teamallbackend/cache/multilevel/VersionedCacheKeyServiceTest.java`
  - `src/test/java/com/xytgy/teamallbackend/utils/RedisUtilsTest.java`

- [ ] **Step 1: Write the failing package-import smoke checks**

```java
import com.xytgy.teamallbackend.cache.hot.HotCacheOptions;
import com.xytgy.teamallbackend.cache.hot.HotCacheRecord;
import com.xytgy.teamallbackend.cache.hot.HotCacheReadResult;
import com.xytgy.teamallbackend.cache.key.RedisGlobPattern;
import com.xytgy.teamallbackend.cache.metrics.CacheMetrics;
```

Add these imports to the affected tests and compile without moving the classes yet so test compilation fails on missing packages.

- [ ] **Step 2: Run test compile to verify it fails**

Run: `./mvnw -DskipTests test-compile`
Expected: FAIL with missing package errors for `cache.hot`, `cache.key`, or `cache.metrics`

- [ ] **Step 3: Move and repackage the low-coupling classes**

Update the package declaration of each class to match its new home:

```java
package com.xytgy.teamallbackend.cache.metrics;
```

```java
package com.xytgy.teamallbackend.cache.key;
```

```java
package com.xytgy.teamallbackend.cache.hot;
```

Also update imports inside moved files so they reference the new package locations.

- [ ] **Step 4: Update test imports and compile**

Update affected tests to import the moved classes from their new packages, then run:

Run: `./mvnw -DskipTests test-compile`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/xytgy/teamallbackend/cache/metrics \
  src/main/java/com/xytgy/teamallbackend/cache/key \
  src/main/java/com/xytgy/teamallbackend/cache/hot \
  src/test/java/com/xytgy/teamallbackend/cache/multilevel/RedisGlobPatternTest.java \
  src/test/java/com/xytgy/teamallbackend/cache/multilevel/VersionedCacheKeyServiceTest.java \
  src/test/java/com/xytgy/teamallbackend/utils/RedisUtilsTest.java
git commit -m "refactor: move cache support types into responsibility packages"
```

## Task 2: Move standard and hot cache core services

**Files:**
- Create: `src/main/java/com/xytgy/teamallbackend/cache/standard/`
- Move/Modify:
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/MultiLevelCacheService.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheCodec.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheCoordinator.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheLockService.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheService.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheStore.java`
- Test:
  - `src/test/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheLockServiceTest.java`
  - `src/test/java/com/xytgy/teamallbackend/utils/RedisUtilsTest.java`

- [ ] **Step 1: Write the failing service-import updates**

Update production imports in `RedisUtils.java` and tests to the intended new packages:

```java
import com.xytgy.teamallbackend.cache.hot.HotCacheService;
import com.xytgy.teamallbackend.cache.standard.MultiLevelCacheService;
```

Leave the classes in the old package initially so compile fails.

- [ ] **Step 2: Run compile to verify it fails**

Run: `./mvnw -DskipTests test-compile`
Expected: FAIL with missing `cache.hot` or `cache.standard` service classes

- [ ] **Step 3: Move and repackage the core services**

Use these package declarations:

```java
package com.xytgy.teamallbackend.cache.standard;
```

```java
package com.xytgy.teamallbackend.cache.hot;
```

Adjust imports for moved support types, for example:

```java
import com.xytgy.teamallbackend.cache.metrics.CacheMetrics;
import com.xytgy.teamallbackend.cache.hot.HotCacheOptions;
```

Keep behavior unchanged while moving.

- [ ] **Step 4: Fix visibility issues explicitly**

If package-private classes such as `HotCacheCodec`, `HotCacheStore`, or `HotCacheCoordinator` need to be referenced across their new package boundaries, either:

- keep them package-private if all users move with them, or
- change them to `public` only if cross-package access is required

Do not mix in unrelated refactoring here.

- [ ] **Step 5: Run targeted tests and commit**

Run: `./mvnw clean -Dtest=HotCacheLockServiceTest,RedisUtilsTest test`
Expected: PASS

```bash
git add src/main/java/com/xytgy/teamallbackend/cache/standard \
  src/main/java/com/xytgy/teamallbackend/cache/hot \
  src/test/java/com/xytgy/teamallbackend/cache/multilevel/HotCacheLockServiceTest.java \
  src/test/java/com/xytgy/teamallbackend/utils/RedisUtilsTest.java
git commit -m "refactor: move hot and standard cache services"
```

## Task 3: Move key invalidation and local invalidation chain

**Files:**
- Create: `src/main/java/com/xytgy/teamallbackend/cache/invalidation/`
- Create: `src/main/java/com/xytgy/teamallbackend/cache/local/`
- Move/Modify:
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/LocalCacheInvalidator.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/CacheInvalidationEvent.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/CacheInvalidationEventHandler.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/CacheInvalidationEventPublisher.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/CacheInvalidationMessage.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/CacheInvalidationRetryService.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/CacheInvalidationSyncService.java`
  - `src/main/java/com/xytgy/teamallbackend/cache/multilevel/CacheKeyCleaner.java`
- Test:
  - `src/test/java/com/xytgy/teamallbackend/cache/multilevel/CacheInvalidationEventHandlerTest.java`
  - `src/test/java/com/xytgy/teamallbackend/cache/multilevel/CacheInvalidationSyncServiceTest.java`
  - `src/test/java/com/xytgy/teamallbackend/cache/multilevel/CacheKeyCleanerTest.java`

- [ ] **Step 1: Write the failing import updates for invalidation flow**

Update tests and production classes to the target packages first:

```java
import com.xytgy.teamallbackend.cache.invalidation.CacheKeyCleaner;
import com.xytgy.teamallbackend.cache.invalidation.CacheInvalidationEventPublisher;
import com.xytgy.teamallbackend.cache.local.LocalCacheInvalidator;
```

- [ ] **Step 2: Run compile to verify it fails**

Run: `./mvnw -DskipTests test-compile`
Expected: FAIL with missing invalidation or local package classes

- [ ] **Step 3: Move and repackage invalidation classes**

Use these package declarations:

```java
package com.xytgy.teamallbackend.cache.invalidation;
```

```java
package com.xytgy.teamallbackend.cache.local;
```

Update imports to the new `cache.hot`, `cache.standard`, `cache.key`, and `cache.metrics` packages as needed.

- [ ] **Step 4: Update tests and package declarations**

Move or repackage invalidation-related tests so they compile cleanly against the new package structure.

Run: `./mvnw clean -Dtest=CacheInvalidationEventHandlerTest,CacheInvalidationSyncServiceTest,CacheKeyCleanerTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/xytgy/teamallbackend/cache/invalidation \
  src/main/java/com/xytgy/teamallbackend/cache/local \
  src/test/java/com/xytgy/teamallbackend/cache/multilevel/CacheInvalidationEventHandlerTest.java \
  src/test/java/com/xytgy/teamallbackend/cache/multilevel/CacheInvalidationSyncServiceTest.java \
  src/test/java/com/xytgy/teamallbackend/cache/multilevel/CacheKeyCleanerTest.java
git commit -m "refactor: move cache invalidation flow into dedicated packages"
```

## Task 4: Move `RedisUtils` into cache facade and repair application imports

**Files:**
- Move/Modify:
  - `src/main/java/com/xytgy/teamallbackend/utils/RedisUtils.java`
- Search/Modify:
  - every production file importing `com.xytgy.teamallbackend.utils.RedisUtils`
  - every production file importing moved cache package classes
- Test:
  - `src/test/java/com/xytgy/teamallbackend/utils/RedisUtilsTest.java`

- [ ] **Step 1: Write the failing facade import updates**

Update imports to the intended new facade package:

```java
import com.xytgy.teamallbackend.cache.facade.RedisUtils;
```

Do this in `RedisUtilsTest` and production callers before moving the class so compile fails.

- [ ] **Step 2: Run compile to verify it fails**

Run: `./mvnw -DskipTests test-compile`
Expected: FAIL with missing `cache.facade.RedisUtils`

- [ ] **Step 3: Move and repackage `RedisUtils`**

Set the new package declaration:

```java
package com.xytgy.teamallbackend.cache.facade;
```

Update imports inside `RedisUtils` to the new cache package structure:

```java
import com.xytgy.teamallbackend.cache.hot.HotCacheService;
import com.xytgy.teamallbackend.cache.metrics.CacheMetrics;
import com.xytgy.teamallbackend.cache.standard.MultiLevelCacheService;
```

Keep all public methods and signatures unchanged.

- [ ] **Step 4: Update all remaining application imports**

Run:

```bash
rg -n "cache\\.multilevel|utils\\.RedisUtils" src/main/java src/test/java
```

Replace all remaining old imports with their new package locations.

- [ ] **Step 5: Run targeted tests and commit**

Run: `./mvnw clean -Dtest=RedisUtilsTest test`
Expected: PASS

```bash
git add src/main/java/com/xytgy/teamallbackend/cache/facade \
  src/main/java \
  src/test/java/com/xytgy/teamallbackend/utils/RedisUtilsTest.java
git commit -m "refactor: move RedisUtils into cache facade package"
```

## Task 5: Final cleanup, package-path alignment, and full regression

**Files:**
- Remove or leave empty old package directories under `src/main/java/com/xytgy/teamallbackend/cache/multilevel`
- Move/update test directories if needed to reflect the new production package structure
- Review any docs or comments that still refer to `cache.multilevel`

- [ ] **Step 1: Search for stale package references**

Run:

```bash
rg -n "cache\\.multilevel" src/main/java src/test/java docs
```

Expected: only intentional historical references remain, or no matches in active code

- [ ] **Step 2: Fix any remaining package declarations or imports**

For any leftover source file, replace old declarations such as:

```java
package com.xytgy.teamallbackend.cache.multilevel;
```

with the correct new package declaration.

- [ ] **Step 3: Run the full test suite**

Run: `./mvnw test`
Expected: PASS

- [ ] **Step 4: Verify application startup compiles and context loads**

Run: `./mvnw -Dtest=TeaMallBackendApplicationTests test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java src/test/java docs
git commit -m "refactor: reorganize cache packages by responsibility"
```

## Self-Review

### Spec coverage

- Replace overloaded `cache.multilevel` package: covered by Tasks 1-5
- Group classes by responsibility: covered by Tasks 1-4
- Keep business-facing APIs stable: covered by Task 4
- Limit scope to cache layer and import updates: enforced in every task
- Avoid behavior refactoring during move: explicitly called out in Tasks 2-4

### Placeholder scan

- No `TODO`, `TBD`, or “implement later” placeholders remain
- Each task names exact files, commands, and expected outcomes

### Type consistency

- Target packages are used consistently:
  - `cache.hot`
  - `cache.standard`
  - `cache.invalidation`
  - `cache.key`
  - `cache.local`
  - `cache.metrics`
  - `cache.facade`
