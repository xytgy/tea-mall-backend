package com.xytgy.teamallbackend.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.lang.Nullable;

import java.util.function.Supplier;

/**
 * Redis 操作安全执行器。
 * <p>当 Redis 不可达时静默降级（fail-open），返回调用方提供的 fallback 值，
 * 防止 Redis 故障时整个业务链路瘫痪。
 */
@Slf4j
public final class RedisSafeRunner {

    private RedisSafeRunner() {}

    /** 执行 Redis 操作，失败时返回 fallback 值。 */
    public static <T> T execute(Supplier<T> action, T fallback) {
        return execute(action, fallback, null);
    }

    /** 执行 Redis 操作，失败时返回 fallback 值，并执行降级回调。 */
    public static <T> T execute(Supplier<T> action, T fallback, @Nullable Runnable onFallback) {
        try {
            return action.get();
        } catch (RedisConnectionFailureException e) {
            log.error("Redis 不可用，fail-open 放行", e);
            if (onFallback != null) onFallback.run();
            return fallback;
        } catch (DataAccessException e) {
            log.error("Redis 访问异常，fail-open 放行", e);
            if (onFallback != null) onFallback.run();
            return fallback;
        }
    }
}
