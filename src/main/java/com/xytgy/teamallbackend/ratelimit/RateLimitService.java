package com.xytgy.teamallbackend.ratelimit;

import com.xytgy.teamallbackend.common.RedisSafeRunner;
import com.xytgy.teamallbackend.properties.RateLimitProperties;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 基于 Redis 的登录速率限制服务。
 * <p>
 * <strong>职责范围：</strong>仅限登录场景的账号/IP 双维度限流，
 * 不包含全局限流（令牌桶）或秒杀专用限流。
 * 如需新增其他场景的限流，请新增独立 Service 或扩展本类。
 * </p>
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
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RateLimitProperties rateLimitProperties;
    private final RateLimitMetrics rateLimitMetrics;

    @Getter
    private String keyPrefix;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

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
     * ARGV[5] = 请求唯一后缀（防 ZADD member 同一毫秒覆盖）
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
            local suffix = ARGV[5]

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

            -- 添加当前请求（member 追加随机后缀，防止同一毫秒覆盖）
            redis.call('ZADD', windowKey, now, now .. ":" .. suffix)
            redis.call('EXPIRE', windowKey, math.ceil(windowMs / 1000) + 1)

            return maxRequests - count - 1
            """;

    public static final long FLAG_LIMITED = -1L;
    public static final long FLAG_LOCKED = -2L;

    /** 原子清除账号限流键的 Lua 脚本。KEYS[1-3] = sw:min, sw:hour, lock */
    private static final String RESET_SCRIPT = """
            for i = 1, #KEYS do
                redis.call('DEL', KEYS[i])
            end
            return 1
            """;

    private DefaultRedisScript<Long> slidingWindowScript;
    private DefaultRedisScript<Long> resetScript;

    @PostConstruct
    void init() {
        this.keyPrefix = "rate:" + activeProfile + ":";
        slidingWindowScript = new DefaultRedisScript<>(SLIDING_WINDOW_SCRIPT, Long.class);
        resetScript = new DefaultRedisScript<>(RESET_SCRIPT, Long.class);
    }

    private RateLimitProperties.Login loginProps() {
        return rateLimitProperties.getLogin();
    }

    // ==================== 账号维度 ====================

    /**
     * 检查指定账号是否已被锁定。
     */
    public boolean isAccountLocked(String identifier) {
        Assert.notNull(identifier, "identifier must not be null");
        return RedisSafeRunner.execute(() -> {
            String lockKey = keyPrefix + LOGIN_RATE_PREFIX + identifier + ":lock";
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey));
        }, false);
    }

    /**
     * 获取账号锁定剩余秒数。
     *
     * @return 剩余锁定秒数，0 表示未锁定
     */
    public long getAccountLockRemaining(String identifier) {
        Assert.notNull(identifier, "identifier must not be null");
        return RedisSafeRunner.execute(() -> {
            String lockKey = keyPrefix + LOGIN_RATE_PREFIX + identifier + ":lock";
            Long ttl = stringRedisTemplate.getExpire(lockKey, java.util.concurrent.TimeUnit.SECONDS);
            return (ttl != null && ttl > 0) ? ttl : 0L;
        }, 0L);
    }

    /**
     * 记录一次账号登录尝试并检查是否超过速率限制。
     * <p>使用 Redis Sorted Set 实现滑动窗口，比固定窗口更精确：
     * 不按自然分钟截断，而是以请求时刻往前推窗口长度进行计数。
     *
     * @return 剩余可用次数（&ge; 0 允许继续），或 -1（限流），-2（锁定）
     */
    public long checkAccountRate(String identifier) {
        Assert.notNull(identifier, "identifier must not be null");
        var props = loginProps();
        return checkRate(LOGIN_RATE_PREFIX, identifier,
                props.getAccountMaxPerMinute(), props.getAccountMaxPerHour(), props.getAccountLockoutMinutes(),
                rateLimitMetrics::recordAccountRateLimited, rateLimitMetrics::recordAccountLocked, "账号");
    }

    /**
     * 记录一次 IP 登录尝试并检查是否超过速率限制。
     *
     * @return 剩余可用次数（&ge; 0 允许继续），或 -1（限流），-2（锁定）
     */
    public long checkIpRate(String ip) {
        Assert.notNull(ip, "ip must not be null");
        var props = loginProps();
        return checkRate(IP_RATE_PREFIX, ip,
                props.getIpMaxPerMinute(), props.getIpMaxPerHour(), props.getIpLockoutMinutes(),
                rateLimitMetrics::recordIpRateLimited, rateLimitMetrics::recordIpLocked, "IP");
    }

    /**
     * 滑动窗口限流通用方法（账号/IP 双维度共享）。
     */
    private long checkRate(String prefix, String target,
                           int maxPerMinute, int maxPerHour, int lockoutMinutes,
                           Runnable onRateLimited, Runnable onLocked, String dimensionLabel) {
        return RedisSafeRunner.execute(() -> {
            long now = System.currentTimeMillis();

            String minuteKey = keyPrefix + prefix + target + ":sw:min";
            String hourKey = keyPrefix + prefix + target + ":sw:hour";
            String lockKey = keyPrefix + prefix + target + ":lock";

            // 优先检查小时级锁定（锁定状态应优先于分钟级限流）
            Long hourResult = stringRedisTemplate.execute(
                    slidingWindowScript,
                    List.of(hourKey, lockKey),
                    String.valueOf(now),
                    String.valueOf(HOUR_WINDOW_MS),
                    String.valueOf(maxPerHour),
                    String.valueOf(lockoutMinutes * 60),
                    uniqueSuffix());

            if (hourResult == FLAG_LOCKED) {
                onLocked.run();
                log.warn("{} {} 触发小时级速率限制，已锁定 {} 分钟", dimensionLabel, target, lockoutMinutes);
                return FLAG_LOCKED;
            }
            if (hourResult == FLAG_LIMITED) {
                onRateLimited.run();
                return FLAG_LIMITED;
            }

            // 再检查分钟级限流
            Long minuteResult = stringRedisTemplate.execute(
                    slidingWindowScript,
                    List.of(minuteKey, ""),
                    String.valueOf(now),
                    String.valueOf(MINUTE_WINDOW_MS),
                    String.valueOf(maxPerMinute),
                    String.valueOf(-1),
                    uniqueSuffix());

            if (minuteResult == FLAG_LIMITED || minuteResult == FLAG_LOCKED) {
                onRateLimited.run();
                return FLAG_LIMITED;
            }

            return minuteResult;
        }, 0L, rateLimitMetrics::recordRedisFallback);
    }

    // ==================== IP 维度 ====================

    /**
     * 检查指定 IP 是否已被锁定。
     */
    public boolean isIpLocked(String ip) {
        Assert.notNull(ip, "ip must not be null");
        return RedisSafeRunner.execute(() -> {
            String lockKey = keyPrefix + IP_RATE_PREFIX + ip + ":lock";
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey));
        }, false);
    }

    /** 登录成功后重置账号的失败计数器，防止正常用户被误锁定。 */
    public void resetAccountAttempts(String identifier) {
        Assert.notNull(identifier, "identifier must not be null");
        try {
            stringRedisTemplate.execute(resetScript,
                    List.of(
                            keyPrefix + LOGIN_RATE_PREFIX + identifier + ":sw:min",
                            keyPrefix + LOGIN_RATE_PREFIX + identifier + ":sw:hour",
                            keyPrefix + LOGIN_RATE_PREFIX + identifier + ":lock"));
            rateLimitMetrics.recordAccountReset();
        } catch (DataAccessException e) {
            log.warn("重置账号限流计数器异常, identifier={}", identifier, e);
        }
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

    private static String uniqueSuffix() {
        return Long.toString(ThreadLocalRandom.current().nextLong(), 36);
    }
}
