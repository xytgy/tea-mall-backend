package com.xytgy.teamallbackend.lock;

import com.xytgy.teamallbackend.properties.LockProperties;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 基于 Redisson 的分布式锁工具类。
 * <pre>
 * 三种使用方式：
 *
 * 1. ✅ executeWithLock（推荐）—— 回调式，自动加锁/解锁
 *    distributedLock.executeWithLock("order:pay:123", () -&gt; {
 *        // 业务逻辑
 *    });
 *
 * 2. tryLock + unlock —— 原始 API，用于特殊场景（如锁失败需要降级）
 *    if (distributedLock.tryLock("key")) {
 *        try {
 *            // ...
 *        } finally {
 *            distributedLock.unlock("key");
 *        }
 *    }
 *
 * 3. tryLockAsGuard —— try-with-resources
 *    try (var guard = distributedLock.tryLockAsGuard("key")) {
 *        if (!guard.isAcquired()) { ... }
 *    }
 * </pre>
 */
@Slf4j
@Component
public class DistributedLock {

    private final RedissonClient redissonClient;
    private final LockProperties lockProperties;

    public DistributedLock(RedissonClient redissonClient, LockProperties lockProperties) {
        this.redissonClient = redissonClient;
        this.lockProperties = lockProperties;
    }

    // ==================== Raw API（向后兼容） ====================

    /**
     * 尝试获取分布式锁（Watch Dog 自动续期）。
     *
     * @param key 锁的业务标识
     * @return true 获取成功，false 已被其他线程持有
     */
    public boolean tryLock(String key) {
        validateKey(key);
        try {
            RLock lock = redissonClient.getLock(lockProperties.getKeyPrefix() + key);
            return lock.tryLock(lockProperties.getDefaultWaitMs(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("获取分布式锁被中断, key={}", key);
            return false;
        }
    }

    /**
     * 尝试获取分布式锁（指定租约时间，固定过期，无 Watch Dog）。
     *
     * @param key         锁的业务标识
     * @param leaseTimeMs 锁持有时间（毫秒），超时自动释放
     * @return true 获取成功，false 已被其他线程持有
     */
    public boolean tryLock(String key, long leaseTimeMs) {
        validateKey(key);
        try {
            RLock lock = redissonClient.getLock(lockProperties.getKeyPrefix() + key);
            return lock.tryLock(lockProperties.getDefaultWaitMs(), leaseTimeMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("获取分布式锁被中断, key={}", key);
            return false;
        }
    }

    /**
     * 释放分布式锁。当前线程未持有锁时静默处理。
     */
    public void unlock(String key) {
        validateKey(key);
        RLock lock = redissonClient.getLock(lockProperties.getKeyPrefix() + key);
        try {
            lock.unlock();
        } catch (IllegalMonitorStateException e) {
            // 正常情况：Watch Dog 过期后锁已被自动释放
            log.trace("当前线程未持有锁, key={}", key);
        }
    }

    // ==================== 回调 API（推荐使用） ====================

    /**
     * 获取锁后执行业务逻辑（Watch Dog 自动续期）。
     * 获取锁失败时抛出 {@link LockAcquisitionException}。
     */
    public void executeWithLock(String key, Runnable runnable) {
        executeWithLock(key, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * 获取锁后执行业务逻辑（固定租约，无 Watch Dog）。
     * 获取锁失败时抛出 {@link LockAcquisitionException}。
     */
    public void executeWithLock(String key, long leaseTimeMs, Runnable runnable) {
        executeWithLock(key, leaseTimeMs, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * 获取锁后执行业务逻辑（Watch Dog 自动续期）。
     * 获取锁失败时抛出 {@link LockAcquisitionException}。
     */
    public <T> T executeWithLock(String key, Supplier<T> supplier) {
        return executeWithLockInternal(key, -1, supplier);
    }

    /**
     * 获取锁后执行业务逻辑（固定租约，无 Watch Dog）。
     * 获取锁失败时抛出 {@link LockAcquisitionException}。
     */
    public <T> T executeWithLock(String key, long leaseTimeMs, Supplier<T> supplier) {
        return executeWithLockInternal(key, leaseTimeMs, supplier);
    }

    // ==================== Guard API（try-with-resources） ====================

    /**
     * 尝试获取锁，返回 {@link LockGuard} 用于 try-with-resources。
     * Watch Dog 自动续期。
     */
    public LockGuard tryLockAsGuard(String key) {
        return tryLockAsGuardInternal(key, -1);
    }

    /**
     * 尝试获取锁，返回 {@link LockGuard} 用于 try-with-resources。
     * 固定租约，无 Watch Dog。
     */
    public LockGuard tryLockAsGuard(String key, long leaseTimeMs) {
        return tryLockAsGuardInternal(key, leaseTimeMs);
    }

    // ==================== 内部实现 ====================

    private <T> T executeWithLockInternal(String key, long leaseTimeMs, Supplier<T> supplier) {
        validateKey(key);
        long start = System.nanoTime();
        RLock lock = redissonClient.getLock(lockProperties.getKeyPrefix() + key);
        boolean acquired;
        try {
            if (leaseTimeMs < 0) {
                acquired = lock.tryLock(lockProperties.getDefaultWaitMs(), TimeUnit.MILLISECONDS);
            } else {
                acquired = lock.tryLock(lockProperties.getDefaultWaitMs(), leaseTimeMs, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException("获取分布式锁被中断, key=" + key);
        }

        long elapsedMs = elapsedMillis(start);
        if (!acquired) {
            log.warn("获取分布式锁超时, key={}, wait={}ms", key, lockProperties.getDefaultWaitMs());
            throw new LockAcquisitionException(lockProperties.getFailMessage());
        }
        if (elapsedMs > lockProperties.getDefaultWaitMs() / 2) {
            log.warn("获取分布式锁耗时较长, key={}, cost={}ms", key, elapsedMs);
        }

        try {
            return supplier.get();
        } finally {
            try {
                lock.unlock();
            } catch (IllegalMonitorStateException e) {
                log.error("释放分布式锁失败：当前线程未持有锁, key={}", key, e);
            }
        }
    }

    private LockGuard tryLockAsGuardInternal(String key, long leaseTimeMs) {
        validateKey(key);
        RLock lock = redissonClient.getLock(lockProperties.getKeyPrefix() + key);
        boolean acquired;
        try {
            if (leaseTimeMs < 0) {
                acquired = lock.tryLock(lockProperties.getDefaultWaitMs(), TimeUnit.MILLISECONDS);
            } else {
                acquired = lock.tryLock(lockProperties.getDefaultWaitMs(), leaseTimeMs, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            acquired = false;
        }
        return new LockGuard(lock, acquired);
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("锁 key 不能为空");
        }
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /**
     * try-with-resources 锁守卫。仅在 {@link #acquired} 为 true 时执行 unlock。
     */
    @Slf4j
    public static class LockGuard implements AutoCloseable {
        private final RLock lock;
        private final boolean acquired;

        LockGuard(RLock lock, boolean acquired) {
            this.lock = lock;
            this.acquired = acquired;
        }

        public boolean isAcquired() {
            return acquired;
        }

        @Override
        public void close() {
            if (!acquired) return;
            try {
                lock.unlock();
            } catch (IllegalMonitorStateException e) {
                log.warn("释放锁失败：未持有, key={}", lock.getName());
            }
        }
    }
}
