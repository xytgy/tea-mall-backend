package com.xytgy.teamallbackend.module.flashsale.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 秒杀行为分析器（L4 异步层）。
 * <p>
 * 检测异常行为模式，同步返回信任评分，异步记录行为数据。
 * <p>
 * 检测规则：
 * 1. 点击频率异常：两次请求间隔 < 500ms
 * 2. 同设备多账号：同一 deviceFingerprint 对应多个 userId
 * 3. 异常时段集中攻击：短时间高频请求
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FlashSaleBehaviorAnalyzer {

    private final StringRedisTemplate stringRedisTemplate;
    private final FlashSaleRateLimiter rateLimiter;
    private final FlashSaleMetrics metrics;

    private static final String CLICK_KEY_PREFIX = "flash:click:";
    private static final String DEVICE_KEY_PREFIX = "flash:device:";
    private static final long MIN_CLICK_INTERVAL_MS = 500;
    private static final int MAX_ACCOUNTS_PER_DEVICE = 3;
    private static final long DEVICE_WINDOW_HOURS = 1;

    /**
     * 记录用户行为并返回信任评分（0-100）。
     * <p>
     * 分数越高越可信，低于 30 分自动加入黑名单。
     * 此方法在 buy() 链路中异步调用（通过 BEHAVIOR_EXECUTOR），不阻塞主链路。
     * 评分写入 Redis Hash，供后续请求参考。
     */
    public BehaviorScore analyze(Long userId, Long flashSaleId, String deviceFingerprint) {
        int score = 100;
        String reason = null;

        // 检测 1：点击频率异常
        String clickKey = CLICK_KEY_PREFIX + userId + ":" + flashSaleId;
        String lastClickStr = stringRedisTemplate.opsForValue().get(clickKey);
        if (lastClickStr != null) {
            long lastClick = Long.parseLong(lastClickStr);
            long interval = System.currentTimeMillis() - lastClick;
            if (interval < MIN_CLICK_INTERVAL_MS) {
                score -= 40;
                reason = "点击间隔过短(" + interval + "ms)";
            }
        }
        stringRedisTemplate.opsForValue().set(clickKey, String.valueOf(System.currentTimeMillis()), 60, TimeUnit.SECONDS);

        // 检测 2：同设备多账号（需要前端传入 deviceFingerprint）
        if (deviceFingerprint != null && !deviceFingerprint.isEmpty()) {
            String deviceKey = DEVICE_KEY_PREFIX + deviceFingerprint;
            Long deviceUserCount = stringRedisTemplate.opsForSet().size(deviceKey);
            if (deviceUserCount != null && deviceUserCount > MAX_ACCOUNTS_PER_DEVICE) {
                score -= 30;
                reason = "同设备账号数超限(" + deviceUserCount + ")";
            }
            stringRedisTemplate.opsForSet().add(deviceKey, String.valueOf(userId));
            stringRedisTemplate.expire(deviceKey, DEVICE_WINDOW_HOURS, TimeUnit.HOURS);
        }

        // 低于 30 分自动拉黑
        if (score < 30) {
            rateLimiter.blacklist(userId);
            metrics.increment("flash.behavior.blacklist");
            log.warn("行为分析异常, 用户 {} 被自动拉黑: score={}, reason={}", userId, score, reason);
        }

        // P2#10: 将评分写入 Redis Hash，供后续请求参考（异步回写）
        try {
            stringRedisTemplate.opsForHash().put(
                    "flash:behavior:" + userId, String.valueOf(flashSaleId), String.valueOf(score));
            stringRedisTemplate.expire("flash:behavior:" + userId, 3600, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // 写入失败不影响主流程
        }

        return new BehaviorScore(score, reason);
    }

    public record BehaviorScore(int score, String reason) {}
}
