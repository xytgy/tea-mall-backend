package com.xytgy.teamallbackend.config.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xytgy.teamallbackend.module.teacircle.service.TeaNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstants.TOPIC_TEA_NOTIFICATION,
        consumerGroup = "tea-mall-tea-notification-group",
        selectorExpression = MqConstants.TAG_LIKE + " || " + MqConstants.TAG_COMMENT
)
public class TeaNotificationConsumer implements RocketMQListener<String> {

    private final TeaNotificationService teaNotificationService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String body) {
        try {
            JsonNode json = objectMapper.readTree(body);
            Long targetUserId = json.get("targetUserId").asLong();
            String type = json.get("type").asText();
            Long sourceId = json.get("sourceId").asLong();
            Long actorId = json.get("actorId").asLong();

            teaNotificationService.addNotification(targetUserId, type, sourceId, actorId);
            log.info("茶友圈通知创建成功: targetUserId={}, type={}, actorId={}", targetUserId, type, actorId);
        } catch (Exception e) {
            log.error("茶友圈通知处理异常, body={}", body, e);
        }
    }
}
