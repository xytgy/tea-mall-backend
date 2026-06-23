package com.xytgy.teamallbackend.ratelimit;

import com.xytgy.teamallbackend.properties.RateLimitProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.function.Supplier;

/**
 * 基于 Redis 的登录速率限制服务。
 * <p>
 * 双维度策略：
 * - 账号维度：同一账号每分钟最多 N 次，每小时最多 N 次，超限锁定
 * - IP 维度：同一 IP 每分钟最多 N 次，每小时最多 N 次（防御分布式密码喷射）
 * </p>
 * <p>
 * 所有限流阈值通过 {@link RateLimitProperties.Login} 配置，支持环境差异化。
 * 限流计数器使用 Lua 脚本保证原子性，消除 INCR + EXPIRE 间的竞态条件。
 * </p>
 */
@Slf4j
@Component
public class RateLimitService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RateLimitProperties rateLimitProperties;
    private final RateLimitMetrics rateLimitMetrics;
    private final String keyPrefix;

    public RateLimitService(StringRedisTemplate stringRedisTemplate,
                            RateLimitProperties rateLimitProperties,
                            RateLimitMetrics rateLimitMetrics,
                            @org.springframework.beans.factory.annotation.Value("${spring.profiles.active:default}") String activeProfile) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.rateLimitProperties = rateLimitProperties;
        this.rateLimitMetrics = rateLimitMetrics;
        this.keyPrefix = "rate:" + activeProfile + ":";
    }

    private static final String LOGIN_RATE_PREFIX = "login:";
    private static final String IP_RATE_PREFIX = "ip:";

    /** 分钟窗口的毫秒数。 */
    private static final long MINUTE_WINDOW_MS = 60_000L;
    /** 小时窗口的毫秒数。 */
    private static final long HOUR_WINDOW_MS = 3_600_000L;

    /**
     * 滑动窗口限流 Lua 脚本（Sorted Set 实现）。
     * <p>
     * KEYS[1] = 当前级别（分钟/小时）的滑动窗口 key<br>
     * KEYS[2] = 锁定 key<br>
     * ARGV[1] = 当前时间戳（毫秒）<br>
     * ARGV[2] = 窗口大小（毫秒）<br>
     * ARGV[3] = 窗口内允许的最大请求数<br>
     * ARGV[4] = 锁定时间（秒），-1 表示不锁定<br>
     * </p>
     * 返回值：&ge; 0 剩余可用次数，-1 限流，-2 锁定
     */
    private static final String SLIDING_WINDOW_SCRIPT = """
            local windowKey = KEYS[1]
            local lockKey = KEYS[2]
            local now = tonumber(ARGV[1])
            local windowMs = tonumber(ARGV[2])
            local maxRequests = tonumber(ARGV[3])
            local lockoutSecs = tonumber(ARGV[4])

            -- 锁定检查
            if lockKey ~= '' then
                local locked = redis.call('EXISTS', lockKey)
                if locked == 1 then return -2 end
            end

            local windowStart = now - windowMs

            -- 清理过期元素
            redis.call('ZREMRANGEBYSCORE', windowKey, '-inf', windowStart)

            -- 统计窗口内请求数
            local count = redis.call('ZCARD', windowKey)

            if count >= maxRequests then
                -- 超限，需要锁定时设置锁定 key
                if lockoutSecs > 0 then
                    redis.call('SET', lockKey, '1', 'EX', lockoutSecs)
                end
                return -1
            end

            -- 添加当前请求
            redis.call('ZADD', windowKey, now, now)
            redis.call('EXPIRE', windowKey, math.ceil(windowMs / 1000) + 1)

            return maxRequests - count - 1
            """;

    public static final long FLAG_LIMITED = -1L;
    public static final long FLAG_LOCKED = -2L;

    /**
     * 获取 IP 维度每分钟最大次数（供调用方设置响应头）。
     */
    public int getIpMaxPerMinute() {
        return loginProps().getIpMaxPerMinute();
    }

    /**
     * 获取账号维度每分钟最大次数（供调用方设置响应头）。
     */
    public int getAccountMaxPerMinute() {
        return loginProps().getAccountMaxPerMinute();
    }

    private DefaultRedisScript<Long> slidingWindowScript;

    @PostConstruct
    void init() {
        slidingWindowScript = new DefaultRedisScript<>(SLIDING_WINDOW_SCRIPT, Long.class);
    }

    private RateLimitProperties.Login loginProps() {
        return rateLimitProperties.getLogin();
    }

    // ==================== 账号维度 ====================

    /**
     * 检查指定账号是否已被锁定。
     */
    public boolean isAccountLocked(String identifier) {
        return executeWithFallback(() -> {
            String lockKey = keyPrefix + LOGIN_RATE_PREFIX + identifier + ":lock";
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey));
        }, false);
    }

    /**
     * 记录一次账号登录尝试并检查是否超过速率限制。
     * <p>使用 Redis Sorted Set 实现滑动窗口，比固定窗口更精确：
     * 不按自然分钟截断，而是以请求时刻往前推窗口长度进行计数。
     *
     * @return 剩余可用次数（&ge; 0 允许继续），或 -1（限流），-2（锁定）
     */
    public long checkAccountRate(String identifier) {
        return executeWithFallback(() -> {
            var props = loginProps();
            long now = System.currentTimeMillis();

            // 先检查分钟级滑动窗口
            String minuteKey = keyPrefix + LOGIN_RATE_PREFIX + identifier + ":sw:min";
            String lockKey = keyPrefix + LOGIN_RATE_PREFIX + identifier + ":lock";

            Long minuteResult = stringRedisTemplate.execute(
                    slidingWindowScript,
                    List.of(minuteKey, ""),
                    String.valueOf(now),
                    String.valueOf(MINUTE_WINDOW_MS),
                    String.valueOf(props.getAccountMaxPerMinute()),
                    String.valueOf(-1));

            if (minuteResult == null) return 0L;
            if (minuteResult == FLAG_LIMITED || minuteResult == FLAG_LOCKED) {
                rateLimitMetrics.recordAccountRateLimited();
                return FLAG_LIMITED;
            }

            // 再检查小时级滑动窗口
            String hourKey = keyPrefix + LOGIN_RATE_PREFIX + identifier + ":sw:hour";

            Long hourResult = stringRedisTemplate.execute(
                    slidingWindowScript,
                    List.of(hourKey, lockKey),
                    String.valueOf(now),
                    String.valueOf(HOUR_WINDOW_MS),
                    String.valueOf(props.getAccountMaxPerHour()),
                    String.valueOf(props.getAccountLockoutMinutes() * 60));

            if (hourResult == null) return minuteResult;
            if (hourResult == FLAG_LOCKED) {
                rateLimitMetrics.recordAccountLocked();
                log.warn("账号 {} 触发小时级速率限制，已锁定 {} 分钟",
                        identifier, props.getAccountLockoutMinutes());
                return FLAG_LOCKED;
            }
            if (hourResult == FLAG_LIMITED) {
                rateLimitMetrics.recordAccountRateLimited();
                return FLAG_LIMITED;
            }

            // 返回分钟级别的剩余次数（更精确的剩余配额指标）
            return minuteResult;
        }, 0L);
    }

    /**
     * 登录成功后重置账号的失败计数器，防止正常用户被误锁定。
     */
    public void resetAccountAttempts(String identifier) {
        try {
            stringRedisTemplate.delete(keyPrefix + LOGIN_RATE_PREFIX + identifier + ":sw:min");
            stringRedisTemplate.delete(keyPrefix + LOGIN_RATE_PREFIX + identifier + ":sw:hour");
            stringRedisTemplate.delete(keyPrefix + LOGIN_RATE_PREFIX + identifier + ":lock");
            rateLimitMetrics.recordAccountReset();
        } catch (DataAccessException e) {
            log.warn("重置账号限流计数器异常, identifier={}", identifier, e);
        }
    }

    // ==================== IP 维度 ====================

    /**
     * 检查指定 IP 是否已被锁定。
     */
    public boolean isIpLocked(String ip) {
        return executeWithFallback(() -> {
            String lockKey = keyPrefix + IP_RATE_PREFIX + ip + ":lock";
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey));
        }, false);
    }

    /**
     * 记录一次 IP 登录尝试并检查是否超过速率限制。
     * <p>使用 Redis Sorted Set 实现滑动窗口，与 {@link #checkAccountRate} 共享同一算法。
     *
     * @return 剩余可用次数（&ge; 0 允许继续），或 -1（限流），-2（锁定）
     */
    public long checkIpRate(String ip) {
        return executeWithFallback(() -> {
            var props = loginProps();
            long now = System.currentTimeMillis();

            String minuteKey = keyPrefix + IP_RATE_PREFIX + ip + ":sw:min";
            String lockKey = keyPrefix + IP_RATE_PREFIX + ip + ":lock";

            Long minuteResult = stringRedisTemplate.execute(
                    slidingWindowScript,
                    List.of(minuteKey, ""),
                    String.valueOf(now),
                    String.valueOf(MINUTE_WINDOW_MS),
                    String.valueOf(props.getIpMaxPerMinute()),
                    String.valueOf(-1));

            if (minuteResult == null) return 0L;
            if (minuteResult == FLAG_LIMITED || minuteResult == FLAG_LOCKED) {
                rateLimitMetrics.recordIpRateLimited();
                return FLAG_LIMITED;
            }

            String hourKey = keyPrefix + IP_RATE_PREFIX + ip + ":sw:hour";

            Long hourResult = stringRedisTemplate.execute(
                    slidingWindowScript,
                    List.of(hourKey, lockKey),
                    String.valueOf(now),
                    String.valueOf(HOUR_WINDOW_MS),
                    String.valueOf(props.getIpMaxPerHour()),
                    String.valueOf(props.getIpLockoutMinutes() * 60));

            if (hourResult == null) return minuteResult;
            if (hourResult == FLAG_LOCKED) {
                rateLimitMetrics.recordIpLocked();
                log.warn("IP {} 触发小时级速率限制，已锁定 {} 分钟",
                        ip, props.getIpLockoutMinutes());
                return FLAG_LOCKED;
            }
            if (hourResult == FLAG_LIMITED) {
                rateLimitMetrics.recordIpRateLimited();
                return FLAG_LIMITED;
            }

            return minuteResult;
        }, 0L);
    }

    /**
     * 检查限流结果是否允许通过。
     */
    public static boolean isAllowed(long rateResult) {
        return rateResult >= 0;
    }

    /**
     * 检查限流结果是否因锁定而禁止。
     */
    public static boolean isLocked(long rateResult) {
        return rateResult == FLAG_LOCKED;
    }

    // ==================== 内部工具 ====================

    /**
     * 执行 Redis 限流逻辑，Redis 不可用时 fail-open 放行请求。
     * <p>
     * 速率限制是辅助安全手段，Redis 宕机时不应导致全站登录不可用。
     * </p>
     */
    private <T> T executeWithFallback(Supplier<T> action, T fallback) {
        try {
            return action.get();
        } catch (RedisConnectionFailureException e) {
            log.error("Redis 不可用，限流服务 fail-open 放行", e);
            rateLimitMetrics.recordRedisFallback();
            return fallback;
        } catch (DataAccessException e) {
            log.error("Redis 访问异常，限流服务 fail-open 放行", e);
            rateLimitMetrics.recordRedisFallback();
            return fallback;
        }
    }
}
