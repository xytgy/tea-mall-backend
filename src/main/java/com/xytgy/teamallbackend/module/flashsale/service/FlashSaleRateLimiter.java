package com.xytgy.teamallbackend.module.flashsale.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 滑动窗口的秒杀梯度限流器，支持运行时动态调整阈值。
 * <p>
 * 优化点：
 * 1. 滑动窗口通过 Lua 脚本在 Redis 端原子执行，1 次 RTT 完成清理+计数+记录+过期
 * 2. 白名单从 Redis Set 读取（SISMEMBER），不再每次查 MySQL
 * 3. 本地维护 5 秒配置缓存，避免每次请求读 Redis Hash
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FlashSaleRateLimiter {

    private final StringRedisTemplate stringRedisTemplate;

    // Redis Key 常量
    private static final String RATE_KEY_PREFIX = "flash:rate:";
    private static final String BLACKLIST_KEY_PREFIX = "flash:blacklist:";
    private static final String WHITELIST_KEY_PREFIX = "flash:whitelist:";
    private static final String CONFIG_KEY = "flash:config:rate";

    // Lua 脚本（滑动窗口限流，1 次 RTT 原子完成）
    private DefaultRedisScript<Long> slidingWindowScript;

    // 本地配置缓存（5 秒过期），避免每次请求读 Redis Hash
    private volatile long localFrequentThreshold = 10;
    private volatile long localMaliciousThreshold = 100;
    private volatile long localBlacklistMinutes = 10;
    private volatile long localConfigLastLoad = 0;
    private static final long LOCAL_CACHE_TTL_MS = 5000;

    @PostConstruct
    public void init() {
        // 预编译 Lua 脚本，后续执行直接用 SHA1，无需每次传输脚本体
        slidingWindowScript = new DefaultRedisScript<>();
        slidingWindowScript.setLocation(new ClassPathResource("lua/sliding_window_limit.lua"));
        slidingWindowScript.setResultType(Long.class);
        // 启动时加载限流配置到本地缓存
        loadConfigFromRedis();
    }

    /**
     * 梯度限流判断：黑名单 → 白名单豁免 → 频率检查
     * <p>
     * 最坏情况 RTT：1 EXISTS + 1 SISMEMBER + 1 Lua = 3 次（相比优化前的 4+1=5 次）
     */
    public RateLimitResult check(Long userId, Long flashSaleId) {
        refreshConfigIfNeeded();

        // L1 黑名单检查（1 次 EXISTS）
        if (isBlacklisted(userId)) {
            return blocked("操作过于频繁，请 " + localBlacklistMinutes + " 分钟后重试");
        }

        // L1.5 白名单检查（1 次 SISMEMBER，从 Redis Set 读取，不再查 DB）
        if (isWhiteListed(userId, flashSaleId)) {
            return allowed("白名单用户，放行");
        }

        // L2 频率检查（1 次 Lua 脚本，合并清理+计数+记录+过期）
        String rateKey = RATE_KEY_PREFIX + userId + ":" + flashSaleId;
        Long count = stringRedisTemplate.execute(
                slidingWindowScript,
                List.of(rateKey),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(60_000L),
                UUID.randomUUID().toString());

        long requestCount = count != null ? count : 0;

        // 超过恶意阈值：直接拉黑
        if (requestCount >= localMaliciousThreshold) {
            blacklist(userId);
            return blocked("检测到恶意行为，账号已被临时限制");
        }

        // 超过频繁阈值：要求验证码
        if (requestCount >= localFrequentThreshold) {
            return requireCaptcha("请求过于频繁，请输入验证码");
        }

        return allowed("放行");
    }

    /**
     * 将用户加入黑名单，自动过期
     */
    public void blacklist(Long userId) {
        stringRedisTemplate.opsForValue().set(
                BLACKLIST_KEY_PREFIX + userId, "1", localBlacklistMinutes, TimeUnit.MINUTES);
        log.warn("用户 {} 被加入秒杀黑名单, {} 分钟后自动解除", userId, localBlacklistMinutes);
    }

    /**
     * 检查用户是否在黑名单中
     */
    public boolean isBlacklisted(Long userId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(BLACKLIST_KEY_PREFIX + userId));
    }

    /**
     * 白名单检查：从 Redis Set 读取（SISMEMBER），避免每次查 MySQL
     * <p>
     * 白名单数据通过 {@link #syncWhitelistToRedis} 预热到 Redis
     */
    public boolean isWhiteListed(Long userId, Long flashSaleId) {
        return Boolean.TRUE.equals(
                stringRedisTemplate.opsForSet().isMember(
                        WHITELIST_KEY_PREFIX + flashSaleId, String.valueOf(userId)));
    }

    /**
     * 预热白名单到 Redis Set（由管理后台或活动预热时调用）
     * <p>
     * TTL 设为 7 天，与活动生命周期一致
     */
    public void syncWhitelistToRedis(Long flashSaleId, List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return;
        String[] members = userIds.stream().map(String::valueOf).toArray(String[]::new);
        stringRedisTemplate.opsForSet().add(WHITELIST_KEY_PREFIX + flashSaleId, members);
        stringRedisTemplate.expire(WHITELIST_KEY_PREFIX + flashSaleId, 7, TimeUnit.DAYS);
    }

    /**
     * 动态更新限流配置（写入 Redis Hash，所有实例 5 秒内生效）
     */
    public void updateConfig(Long frequentThreshold, Long maliciousThreshold, Long blacklistMinutes) {
        if (frequentThreshold != null && frequentThreshold > 0) {
            stringRedisTemplate.opsForHash().put(CONFIG_KEY, "frequentThreshold", String.valueOf(frequentThreshold));
        }
        if (maliciousThreshold != null && maliciousThreshold > 0) {
            stringRedisTemplate.opsForHash().put(CONFIG_KEY, "maliciousThreshold", String.valueOf(maliciousThreshold));
        }
        if (blacklistMinutes != null && blacklistMinutes > 0) {
            stringRedisTemplate.opsForHash().put(CONFIG_KEY, "blacklistMinutes", String.valueOf(blacklistMinutes));
        }
        // 立即刷新本地缓存，当前实例即时生效
        loadConfigFromRedis();
        log.info("限流配置已更新: frequent={}, malicious={}, blacklistMinutes={}",
                localFrequentThreshold, localMaliciousThreshold, localBlacklistMinutes);
    }

    /**
     * 获取当前生效的限流配置快照
     */
    public Map<String, Object> getConfig() {
        refreshConfigIfNeeded();
        return Map.of(
                "frequentThreshold", localFrequentThreshold,
                "maliciousThreshold", localMaliciousThreshold,
                "blacklistMinutes", localBlacklistMinutes);
    }

    // ==================== 私有方法 ====================

    /**
     * 从 Redis Hash 加载限流配置到本地缓存
     */
    private void loadConfigFromRedis() {
        try {
            Map<Object, Object> config = stringRedisTemplate.opsForHash().entries(CONFIG_KEY);
            if (!config.isEmpty()) {
                localFrequentThreshold = parseLong(config.get("frequentThreshold"), 10);
                localMaliciousThreshold = parseLong(config.get("maliciousThreshold"), 100);
                localBlacklistMinutes = parseLong(config.get("blacklistMinutes"), 10);
            }
            localConfigLastLoad = System.currentTimeMillis();
        } catch (Exception e) {
            log.warn("加载限流配置失败，使用本地默认值: {}", e.getMessage());
        }
    }

    private long parseLong(Object val, long defaultVal) {
        if (val == null) return defaultVal;
        try {
            return Long.parseLong(val.toString());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    /**
     * 本地缓存过期时异步刷新，避免每次请求都读 Redis Hash
     */
    // P1#11: double-check locking 防止缓存过期瞬间大量线程同时刷新 Redis
    private void refreshConfigIfNeeded() {
        if (System.currentTimeMillis() - localConfigLastLoad > LOCAL_CACHE_TTL_MS) {
            synchronized (this) {
                if (System.currentTimeMillis() - localConfigLastLoad > LOCAL_CACHE_TTL_MS) {
                    loadConfigFromRedis();
                }
            }
        }
    }

    private RateLimitResult allowed(String message) {
        return new RateLimitResult(true, false, false, message);
    }

    private RateLimitResult blocked(String message) {
        return new RateLimitResult(false, false, true, message);
    }

    private RateLimitResult requireCaptcha(String message) {
        return new RateLimitResult(true, true, false, message);
    }

    public record RateLimitResult(boolean allow, boolean requireCaptcha, boolean blocked, String message) {}
}
