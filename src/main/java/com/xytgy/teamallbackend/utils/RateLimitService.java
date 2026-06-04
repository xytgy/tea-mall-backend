package com.xytgy.teamallbackend.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的登录速率限制服务
 * <p>
 * 双维度策略：
 * - 账号维度：同一账号每分钟最多 10 次，每小时最多 30 次，超限锁定 15 分钟
 * - IP 维度：同一 IP 每分钟最多 20 次，每小时最多 100 次（防御分布式密码喷射）
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String LOGIN_RATE_PREFIX = "rate:login:";
    private static final String IP_RATE_PREFIX = "rate:ip:";

    private static final int ACCOUNT_MAX_PER_MINUTE = 10;
    private static final int ACCOUNT_MAX_PER_HOUR = 30;
    private static final int ACCOUNT_LOCKOUT_MINUTES = 15;

    private static final int IP_MAX_PER_MINUTE = 20;
    private static final int IP_MAX_PER_HOUR = 100;
    private static final int IP_LOCKOUT_MINUTES = 15;

    // ==================== 账号维度 ====================

    /**
     * 检查指定账号是否已被锁定
     */
    public boolean isAccountLocked(String identifier) {
        String lockKey = LOGIN_RATE_PREFIX + identifier + ":lock";
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey));
    }

    /**
     * 记录一次账号登录尝试并检查是否超过速率限制
     *
     * @return true 表示允许继续，false 表示已被限流
     */
    public boolean checkAccountRate(String identifier) {
        String minuteKey = LOGIN_RATE_PREFIX + identifier + ":min";
        String hourKey = LOGIN_RATE_PREFIX + identifier + ":hour";

        Long minuteCount = stringRedisTemplate.opsForValue().increment(minuteKey);
        if (minuteCount != null && minuteCount == 1) {
            stringRedisTemplate.expire(minuteKey, 1, TimeUnit.MINUTES);
        }

        Long hourCount = stringRedisTemplate.opsForValue().increment(hourKey);
        if (hourCount != null && hourCount == 1) {
            stringRedisTemplate.expire(hourKey, 1, TimeUnit.HOURS);
        }

        if (minuteCount != null && minuteCount > ACCOUNT_MAX_PER_MINUTE) {
            log.warn("账号 {} 触发分钟级速率限制，当前尝试次数: {}", identifier, minuteCount);
            return false;
        }

        if (hourCount != null && hourCount > ACCOUNT_MAX_PER_HOUR) {
            String lockKey = LOGIN_RATE_PREFIX + identifier + ":lock";
            stringRedisTemplate.opsForValue().set(lockKey, "1", ACCOUNT_LOCKOUT_MINUTES, TimeUnit.MINUTES);
            log.warn("账号 {} 触发小时级速率限制，已锁定 {} 分钟", identifier, ACCOUNT_LOCKOUT_MINUTES);
            return false;
        }

        return true;
    }

    /**
     * 登录成功后重置账号的失败计数器，防止正常用户被误锁定
     */
    public void resetAccountAttempts(String identifier) {
        stringRedisTemplate.delete(LOGIN_RATE_PREFIX + identifier + ":min");
        stringRedisTemplate.delete(LOGIN_RATE_PREFIX + identifier + ":hour");
        stringRedisTemplate.delete(LOGIN_RATE_PREFIX + identifier + ":lock");
    }

    // ==================== IP 维度 ====================

    /**
     * 检查指定 IP 是否已被锁定
     */
    public boolean isIpLocked(String ip) {
        String lockKey = IP_RATE_PREFIX + ip + ":lock";
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey));
    }

    /**
     * 记录一次 IP 登录尝试并检查是否超过速率限制
     *
     * @return true 表示允许继续，false 表示已被限流
     */
    public boolean checkIpRate(String ip) {
        String minuteKey = IP_RATE_PREFIX + ip + ":min";
        String hourKey = IP_RATE_PREFIX + ip + ":hour";

        Long minuteCount = stringRedisTemplate.opsForValue().increment(minuteKey);
        if (minuteCount != null && minuteCount == 1) {
            stringRedisTemplate.expire(minuteKey, 1, TimeUnit.MINUTES);
        }

        Long hourCount = stringRedisTemplate.opsForValue().increment(hourKey);
        if (hourCount != null && hourCount == 1) {
            stringRedisTemplate.expire(hourKey, 1, TimeUnit.HOURS);
        }

        if (minuteCount != null && minuteCount > IP_MAX_PER_MINUTE) {
            log.warn("IP {} 触发分钟级速率限制，当前尝试次数: {}", ip, minuteCount);
            return false;
        }

        if (hourCount != null && hourCount > IP_MAX_PER_HOUR) {
            String lockKey = IP_RATE_PREFIX + ip + ":lock";
            stringRedisTemplate.opsForValue().set(lockKey, "1", IP_LOCKOUT_MINUTES, TimeUnit.MINUTES);
            log.warn("IP {} 触发小时级速率限制，已锁定 {} 分钟", ip, IP_LOCKOUT_MINUTES);
            return false;
        }

        return true;
    }
}
