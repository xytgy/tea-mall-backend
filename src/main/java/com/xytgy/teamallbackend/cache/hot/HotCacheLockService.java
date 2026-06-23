package com.xytgy.teamallbackend.cache.hot;

import com.xytgy.teamallbackend.cache.metrics.CacheMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * 热点缓存分布式锁服务。
 */
@Slf4j
class HotCacheLockService {
    private static final String LOCK_PREFIX = "hot:lock:";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end",
            Long.class);
    private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('pexpire',KEYS[1],ARGV[2]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final CacheMetrics metrics;
    private final ScheduledExecutorService renewExecutor;

    HotCacheLockService(StringRedisTemplate redisTemplate, CacheMetrics metrics) {
        this(redisTemplate, metrics, Executors.newSingleThreadScheduledExecutor(new RenewThreadFactory()));
    }

    HotCacheLockService(StringRedisTemplate redisTemplate, CacheMetrics metrics,
                        ScheduledExecutorService renewExecutor) {
        this.redisTemplate = redisTemplate;
        this.metrics = metrics;
        this.renewExecutor = renewExecutor;
    }

    Optional<LockHandle> tryAcquire(String cacheKey, Duration ttl, Duration renewInterval) {
        String lockKey = LOCK_PREFIX + cacheKey;
        String token = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, token, ttl);
        if (Boolean.TRUE.equals(locked)) {
            metrics.recordHotLockAcquireSuccess();
            return Optional.of(new LockHandle(lockKey, token, ttl, renewInterval));
        }
        metrics.recordHotLockAcquireFailure();
        return Optional.empty();
    }

    final class LockHandle implements AutoCloseable {
        private final String lockKey;
        private final String token;
        private final ScheduledFuture<?> renewTask;
        private boolean closed;

        private LockHandle(String lockKey, String token, Duration ttl, Duration renewInterval) {
            this.lockKey = lockKey;
            this.token = token;
            this.renewTask = scheduleRenew(lockKey, token, ttl, renewInterval);
        }

        String token() {
            return token;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (renewTask != null) {
                renewTask.cancel(false);
            }
            redisTemplate.execute(UNLOCK_SCRIPT, List.of(lockKey), token);
        }
    }

    private ScheduledFuture<?> scheduleRenew(String lockKey, String token,
                                             Duration ttl, Duration renewInterval) {
        if (renewInterval == null || renewInterval.isZero() || renewInterval.isNegative()
                || renewInterval.compareTo(ttl) >= 0) {
            return null;
        }
        long renewMillis = renewInterval.toMillis();
        long ttlMillis = ttl.toMillis();
        return renewExecutor.scheduleAtFixedRate(
                () -> renew(lockKey, token, ttlMillis),
                renewMillis, renewMillis, TimeUnit.MILLISECONDS);
    }

    private void renew(String lockKey, String token, long ttlMillis) {
        try {
            Long result = redisTemplate.execute(RENEW_SCRIPT, List.of(lockKey), token, String.valueOf(ttlMillis));
            if (result == null || result == 0L) {
                metrics.recordHotLockRenewFailure();
            }
        } catch (RuntimeException e) {
            metrics.recordHotLockRenewFailure();
            log.warn("热点锁续约失败, lockKey={}", lockKey, e);
        }
    }

    private static final class RenewThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "hot-cache-lock-renew");
            thread.setDaemon(true);
            return thread;
        }
    }
}
