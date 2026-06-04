package com.xytgy.teamallbackend.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的分布式锁工具类。
 * <p>
 * 加锁使用 SET NX PX（原子操作），释放锁使用 Lua 脚本保证原子性。
 * 每次加锁生成唯一 UUID 作为锁标识，存入 ThreadLocal，
 * 释放时通过 ThreadLocal 取出同一标识进行比对，确保只有持锁者才能释放。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class DistributedLock {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String LOCK_PREFIX = "lock:";
    private static final long DEFAULT_LEASE_SECONDS = 10;

    /**
     * 存储当前线程持有的锁标识（key: 业务锁key, value: UUID）
     */
    private static final ThreadLocal<java.util.Map<String, String>> LOCK_VALUES =
            ThreadLocal.withInitial(java.util.HashMap::new);

    /**
     * 尝试获取分布式锁。
     *
     * @param key         锁的业务标识（如 "order:pay:123"）
     * @param leaseTimeMs 锁的持有时间（毫秒），超时自动释放
     * @return true 表示获取成功，false 表示已被其他线程持有
     */
    public boolean tryLock(String key, long leaseTimeMs) {
        String lockValue = UUID.randomUUID().toString();
        Boolean result = stringRedisTemplate.opsForValue()
                .setIfAbsent(LOCK_PREFIX + key, lockValue, leaseTimeMs, TimeUnit.MILLISECONDS);
        if (Boolean.TRUE.equals(result)) {
            // 获取成功，记录锁标识供 unlock 使用
            LOCK_VALUES.get().put(key, lockValue);
            return true;
        }
        return false;
    }

    /**
     * 尝试获取分布式锁（默认 10 秒自动释放）。
     */
    public boolean tryLock(String key) {
        return tryLock(key, TimeUnit.SECONDS.toMillis(DEFAULT_LEASE_SECONDS));
    }

    /**
     * 释放分布式锁（Lua 脚本保证原子性：仅当锁的持有者才能释放）。
     */
    public void unlock(String key) {
        String lockValue = LOCK_VALUES.get().get(key);
        if (lockValue == null) {
            // 当前线程未持有该锁，跳过释放
            return;
        }
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "return redis.call('del', KEYS[1]) " +
                "else return 0 end";
        stringRedisTemplate.execute(
                new DefaultRedisScript<>(script, Long.class),
                Collections.singletonList(LOCK_PREFIX + key),
                lockValue);
        // 清理 ThreadLocal，防止内存泄漏
        LOCK_VALUES.get().remove(key);
    }
}
