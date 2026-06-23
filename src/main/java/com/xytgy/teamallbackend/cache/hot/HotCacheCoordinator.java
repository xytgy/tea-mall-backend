package com.xytgy.teamallbackend.cache.hot;

import com.xytgy.teamallbackend.cache.metrics.CacheMetrics;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 热点缓存读取主流程协调器。
 */
@RequiredArgsConstructor
class HotCacheCoordinator {
    private final HotCacheStore store;
    private final HotCacheLockService lockService;
    private final CacheMetrics metrics;

    <T> T getOrLoad(String key, Class<T> type, HotCacheOptions options, Supplier<T> loader) {
        HotCacheReadResult<T> read = store.read(key, type);
        if (!read.isHit()) {
            return rebuildOrRetry(key, type, options, loader);
        }
        if (read.isFresh()) {
            return read.getData();
        }
        return rebuildOrServeStale(key, type, options, loader, read);
    }

    <T> T getOrLoad(String key, HotCacheOptions options, Supplier<T> loader) {
        HotCacheReadResult<T> read = store.readUntyped(key);
        if (!read.isHit()) {
            return rebuildOrRetry(key, options, loader);
        }
        if (read.isFresh()) {
            return read.getData();
        }
        return rebuildOrServeStale(key, options, loader, read);
    }

    private <T> T rebuildOrRetry(String key, Class<T> type, HotCacheOptions options, Supplier<T> loader) {
        return tryWithLock(key, options, loader, () -> {
            pause(options.getRetryWait());
            HotCacheReadResult<T> retried = store.read(key, type);
            return retried.isHit() ? retried.getData() : loadAndWrite(key, options, loader);
        });
    }

    private <T> T rebuildOrRetry(String key, HotCacheOptions options, Supplier<T> loader) {
        return tryWithLock(key, options, loader, () -> {
            pause(options.getRetryWait());
            HotCacheReadResult<T> retried = store.readUntyped(key);
            return retried.isHit() ? retried.getData() : loadAndWrite(key, options, loader);
        });
    }

    private <T> T rebuildOrServeStale(String key, Class<T> type, HotCacheOptions options,
                                      Supplier<T> loader, HotCacheReadResult<T> stale) {
        return tryWithLock(key, options, loader, () -> {
            if (withinMaxStale(stale, options.getMaxStaleTtl())) {
                metrics.recordHotStaleReturn();
                return stale.getData();
            }
            pause(options.getRetryWait());
            HotCacheReadResult<T> retried = store.read(key, type);
            if (retried.isHit()) {
                return retried.getData();
            }
            return loadAndWrite(key, options, loader);
        });
    }

    private <T> T rebuildOrServeStale(String key, HotCacheOptions options,
                                      Supplier<T> loader, HotCacheReadResult<T> stale) {
        return tryWithLock(key, options, loader, () -> {
            if (withinMaxStale(stale, options.getMaxStaleTtl())) {
                metrics.recordHotStaleReturn();
                return stale.getData();
            }
            pause(options.getRetryWait());
            HotCacheReadResult<T> retried = store.readUntyped(key);
            if (retried.isHit()) {
                return retried.getData();
            }
            return loadAndWrite(key, options, loader);
        });
    }

    private <T> T tryWithLock(String key, HotCacheOptions options, Supplier<T> loader,
                              Supplier<T> lockMissFallback) {
        var handle = lockService.tryAcquire(key, options.getLockTtl(), options.getLockRenewInterval());
        if (handle.isPresent()) {
            try (var ignored = handle.get()) {
                return loadAndWrite(key, options, loader);
            }
        }
        metrics.recordHotLockContended();
        return lockMissFallback.get();
    }

    private <T> T loadAndWrite(String key, HotCacheOptions options, Supplier<T> loader) {
        T data = metrics.recordSourceLoad(loader);
        store.writeFresh(key, data, options);
        return data;
    }

    private boolean withinMaxStale(HotCacheReadResult<?> stale, Duration maxStale) {
        long staleAge = System.currentTimeMillis() - stale.getLogicalExpireAt();
        return staleAge <= maxStale.toMillis();
    }

    private void pause(Duration retryWait) {
        try {
            Thread.sleep(retryWait.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
