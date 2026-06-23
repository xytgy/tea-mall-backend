# Cache Package Reorganization Design

## Background

The current package:

`com.xytgy.teamallbackend.cache.multilevel`

no longer reflects the actual responsibilities of the code inside it.

It currently mixes several distinct concerns:

- standard multi-level cache logic
- hot-cache logic
- cache invalidation and cross-instance synchronization
- cache key/version helpers
- cache metrics
- local invalidation coordination

As a result, the directory is hard to scan, package naming is misleading, and it is becoming harder to reason about ownership boundaries.

## Goals

- Replace the overloaded `cache.multilevel` package with a clearer package structure
- Group classes by responsibility rather than historical implementation style
- Keep business-facing cache APIs stable
- Limit scope to the cache layer and import updates only
- Avoid mixing behavior refactoring with package reorganization

## Non-Goals

- Redesign all cache algorithms during this change
- Change business-facing cache APIs
- Introduce a new generic cache framework
- Rename unrelated service-layer business abstractions

## Reorganization Strategy

This reorganization should be structural, not behavioral.

That means:

- move classes into new packages
- update package declarations and imports
- keep public behavior unchanged
- keep existing business calling style unchanged

If behavior changes are discovered to be necessary during migration, they should be treated as follow-up work rather than silently folded into the package move.

## Target Package Structure

Replace the current single overloaded package with the following structure:

```text
com.xytgy.teamallbackend.cache
├── facade
│   └── RedisUtils
├── hot
│   ├── HotCacheService
│   ├── HotCacheCoordinator
│   ├── HotCacheStore
│   ├── HotCacheLockService
│   ├── HotCacheCodec
│   ├── HotCacheOptions
│   ├── HotCacheReadResult
│   ├── HotCacheRecord
│   └── HotCacheEntry
├── standard
│   └── MultiLevelCacheService
├── invalidation
│   ├── CacheInvalidationEvent
│   ├── CacheInvalidationEventHandler
│   ├── CacheInvalidationEventPublisher
│   ├── CacheInvalidationMessage
│   ├── CacheInvalidationRetryService
│   ├── CacheInvalidationSyncService
│   └── CacheKeyCleaner
├── key
│   ├── RedisGlobPattern
│   └── VersionedCacheKeyService
├── local
│   └── LocalCacheInvalidator
└── metrics
    └── CacheMetrics
```

## Package Responsibilities

### `cache.facade`

Contains the business-facing cache entry point.

Responsibility:

- stable high-level access API for business code
- shields controllers/services from internal cache package changes

Contents:

- `RedisUtils`

### `cache.hot`

Contains all hot-key cache logic.

Responsibility:

- stale-while-revalidate flow
- hot-cache record model
- hot-cache lock handling
- hot-cache local/Redis read-write coordination

Contents:

- `HotCacheService`
- `HotCacheCoordinator`
- `HotCacheStore`
- `HotCacheLockService`
- `HotCacheCodec`
- `HotCacheOptions`
- `HotCacheReadResult`
- `HotCacheRecord`
- `HotCacheEntry`

### `cache.standard`

Contains the normal multi-level cache implementation.

Responsibility:

- standard L1/L2 cache-aside behavior
- normal null placeholder handling
- standard distributed load protection

Contents:

- `MultiLevelCacheService`

### `cache.invalidation`

Contains cache invalidation execution, eventing, retry, and cross-instance sync.

Responsibility:

- transaction-aware invalidation event publishing
- Redis deletion and retry
- cross-instance invalidation propagation

Contents:

- `CacheInvalidationEvent`
- `CacheInvalidationEventHandler`
- `CacheInvalidationEventPublisher`
- `CacheInvalidationMessage`
- `CacheInvalidationRetryService`
- `CacheInvalidationSyncService`
- `CacheKeyCleaner`

### `cache.key`

Contains cache-key related support logic.

Responsibility:

- pattern validation and conversion
- versioned cache key support

Contents:

- `RedisGlobPattern`
- `VersionedCacheKeyService`

### `cache.local`

Contains local cache invalidation coordination.

Responsibility:

- invalidate current-instance local caches only
- coordinate invalidation across hot and standard caches

Contents:

- `LocalCacheInvalidator`

### `cache.metrics`

Contains cache observability support.

Responsibility:

- cache counters and timers
- cache metrics registration and access

Contents:

- `CacheMetrics`

## Dependency Rules

The new package structure should also make dependency direction explicit.

### Allowed dependencies

- `cache.facade` may depend on all cache subpackages
- `cache.hot` may depend on `cache.metrics`
- `cache.standard` may depend on `cache.metrics`
- `cache.invalidation` may depend on `cache.hot`, `cache.standard`, `cache.key`, `cache.local`, and `cache.metrics`
- `cache.local` may depend on `cache.hot` and `cache.standard`
- `cache.key` may depend on `cache.metrics` if needed

### Disallowed dependencies

- `cache.hot` must not depend on `cache.standard`
- `cache.standard` must not depend on `cache.hot`
- internal packages must not depend on `cache.facade`

These rules are intended to prevent the new structure from collapsing back into a shared miscellaneous package.

## Naming Decisions

### Keep `MultiLevelCacheService`

This name is already meaningful and recognizable. The current issue is package placement, not the class name itself.

### Keep `RedisUtils` public API stable

Although the class may be relocated into `cache.facade`, its calling style should remain unchanged to minimize application-layer impact.

### Keep `HotCacheEntry` temporarily

`HotCacheRecord` is now the primary model, but `HotCacheEntry` still serves compatibility purposes for legacy payload decoding. It should remain during the reorganization and only be removed in a later cleanup once compatibility is no longer required.

## Migration Plan

The move should happen in four phases.

### Phase 1: Create target packages

Create the destination package structure first, without changing behavior.

This prepares the repository for orderly migration and reduces the risk of large, hard-to-review moves.

### Phase 2: Move low-coupling support types

Move the most isolated classes first:

- `CacheMetrics`
- `RedisGlobPattern`
- `VersionedCacheKeyService`
- `HotCacheOptions`
- `HotCacheReadResult`
- `HotCacheRecord`
- `HotCacheEntry`

This step reduces the density of the old package with minimal wiring risk.

### Phase 3: Move core cache services

Move the main standard and hot cache services:

- `MultiLevelCacheService`
- `HotCacheService`
- `HotCacheCoordinator`
- `HotCacheStore`
- `HotCacheLockService`
- `HotCacheCodec`

This is the most sensitive migration step and should focus strictly on package relocation and import repair.

### Phase 4: Move invalidation and facade

Move the most interconnected classes last:

- `LocalCacheInvalidator`
- all `CacheInvalidation*` classes
- `CacheKeyCleaner`
- `RedisUtils`

This ordering is intentional because these classes sit at the center of multiple cache dependencies.

## Visibility Considerations

This reorganization changes package boundaries, so package-private classes and methods must be reviewed carefully.

Particular attention is required for:

- `HotCacheCodec`
- `HotCacheStore`
- `HotCacheCoordinator`
- `RedisGlobPattern`
- package-private invalidation helpers

Any type that must be shared across newly separated packages will need either:

- explicit `public` visibility
- relocation to a more appropriate shared package
- or refactoring to remove the cross-package dependency

This is one of the highest-risk parts of the migration.

## Risk Assessment

### 1. Spring bean wiring risk

Since all new packages remain under `com.xytgy.teamallbackend`, component scanning should continue to work.

The main risk is not scanning itself, but broken constructor wiring caused by incomplete import or visibility updates.

### 2. Test package alignment risk

Some tests currently live under `src/test/java/.../cache/multilevel`.

If production classes move but test package declarations and imports are not updated consistently, compilation and discovery failures will follow.

### 3. Default-visibility breakage

Classes that previously lived in one package may no longer see each other after being split.

This must be handled intentionally rather than fixed ad hoc.

### 4. Backward compatibility risk for hot-cache payload decoding

`HotCacheEntry` remains part of the compatibility path for old Redis payloads.

Moving it is safe, but removing or reshaping it during this package reorganization is not.

### 5. Scope creep risk

Because this change touches many imports, it will be tempting to refactor logic at the same time.

That should be avoided. The success criterion here is clearer package structure, not behavioral redesign.

## Validation Strategy

At minimum, the following checks are required after the migration:

### Targeted tests

- `RedisUtilsTest`
- hot-cache tests
- invalidation-related tests
- key/version-related tests

### Full automated verification

- `./mvnw test`

### Runtime validation

- application context startup succeeds
- cache beans are created correctly
- no bean ambiguity or missing bean errors appear

## Recommended Execution Rules

To keep the migration safe:

- do not combine package reorganization with new cache behavior changes
- move low-risk classes first
- run tests after each migration batch
- keep `RedisUtils` outward behavior unchanged
- treat visibility adjustments as explicit design work, not incidental cleanup

## Recommendation

Proceed with a responsibility-based package split while keeping business-facing cache APIs stable.

This gives the strongest structural improvement with acceptable migration risk and avoids turning a package cleanup into a large cache-system rewrite.
