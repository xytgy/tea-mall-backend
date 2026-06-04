package com.xytgy.teamallbackend.module.flashsale.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xytgy.teamallbackend.config.websocket.ChatWebSocketHandler;
import com.xytgy.teamallbackend.module.flashsale.entity.FlashSaleAuditLog;
import com.xytgy.teamallbackend.module.flashsale.repository.FlashSaleAuditLogMapper;
import com.xytgy.teamallbackend.module.teacircle.entity.TeaNotification;
import com.xytgy.teamallbackend.module.teacircle.repository.TeaNotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 秒杀失败通知服务（三级降级）。
 * <p>
 * P0 站内信：复用 tea_notification 表，type="flash"
 * P1 WebSocket：用户在线时实时推送
 * P2 短信：预留接口（需接入 SMS SDK）
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FlashSaleNotificationService {

    private final TeaNotificationMapper teaNotificationMapper;
    private final ChatWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;
    private final FlashSaleMetrics metrics;

    /**
     * 发送秒杀失败通知（按优先级逐级尝试）。
     *
     * @param userId      接收用户
     * @param flashSaleId 活动ID
     * @param productId   商品ID
     * @param reason      失败原因
     */
    public void sendFailureNotification(Long userId, Long flashSaleId, Long productId, String reason) {
        metrics.increment("flash.notify.total");

        // P0 站内信（默认，必定执行）
        boolean p0Success = sendInAppNotification(userId, flashSaleId, productId, reason);

        // P1 WebSocket 推送（用户在线时）
        boolean p1Success = sendWebSocketNotification(userId, flashSaleId, productId, reason);

        // P2 短信（用户绑定手机号时，预留接口）
        // sendSmsNotification(userId, reason);

        if (p0Success || p1Success) {
            metrics.increment("flash.notify.success");
        }

        log.debug("秒杀失败通知: userId={}, p0={}, p1={}", userId, p0Success, p1Success);
    }

    /**
     * P0 站内信：写入 tea_notification 表。
     * sourceId = flashSaleId * 10000 + productId（简单编码，用于通知列表展示时跳转）
     */
    private boolean sendInAppNotification(Long userId, Long flashSaleId, Long productId, String reason) {
        try {
            TeaNotification notification = new TeaNotification();
            notification.setUserId(userId);
            notification.setType("flash");
            notification.setSourceId(flashSaleId * 10000 + productId);
            notification.setActorId(0L);
            notification.setIsRead(0);
            notification.setCreateTime(LocalDateTime.now());
            teaNotificationMapper.insert(notification);
            return true;
        } catch (Exception e) {
            log.warn("P0 站内信发送失败: userId={}, error={}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * P1 WebSocket：用户在线时实时推送。
     */
    private boolean sendWebSocketNotification(Long userId, Long flashSaleId, Long productId, String reason) {
        try {
            Map<String, Object> payload = Map.of(
                    "type", "FLASH_SALE_FAIL",
                    "flashSaleId", flashSaleId,
                    "productId", productId,
                    "reason", reason,
                    "timestamp", System.currentTimeMillis());
            String json = objectMapper.writeValueAsString(payload);
            return webSocketHandler.sendNotificationToUser(userId, json);
        } catch (Exception e) {
            log.warn("P1 WebSocket 推送失败: userId={}, error={}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * P2 短信通知（预留接口）。
     * <p>
     * 需要接入 SMS SDK（阿里云/腾讯云），当前仅记录日志。
     * 需要检查 user.phone 是否存在。
     */
    @SuppressWarnings("unused")
    private void sendSmsNotification(Long userId, String reason) {
        // TODO: 接入 SMS SDK 后实现
        log.info("P2 短信通知(预留): userId={}, reason={}", userId, reason);
    }
}
