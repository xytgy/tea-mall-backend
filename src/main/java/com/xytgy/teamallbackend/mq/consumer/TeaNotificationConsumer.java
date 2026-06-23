package com.xytgy.teamallbackend.mq.consumer;
import com.xytgy.teamallbackend.mq.constant.MqConstants;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xytgy.teamallbackend.mq.handler.TeaNotificationHandler;
import com.xytgy.teamallbackend.mq.message.teacircle.TeaNotificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rocketmq.name-server")
@RocketMQMessageListener(
        topic = MqConstants.TOPIC_TEA_NOTIFICATION,
        consumerGroup = MqConstants.GROUP_TEA_NOTIFICATION,
        selectorExpression = MqConstants.TAG_LIKE + " || " + MqConstants.TAG_COMMENT
)
public class TeaNotificationConsumer implements RocketMQListener<String> {

    private final ObjectMapper objectMapper;
    private final TeaNotificationHandler teaNotificationHandler;

    @Override
    public void onMessage(String body) {
        try {
            TeaNotificationMessage message = objectMapper.readValue(body, TeaNotificationMessage.class);
            if (message.getTargetUserId() == null
                    || message.getType() == null
                    || message.getSourceId() == null
                    || message.getActorId() == null) {
                throw new IllegalArgumentException("茶友圈通知消息字段不完整");
            }
            teaNotificationHandler.handle(
                    message.getTargetUserId(),
                    message.getType(),
                    message.getSourceId(),
                    message.getActorId()
            );
        } catch (Exception e) {
            log.error("茶友圈通知处理异常, body={}", body, e);
            throw new IllegalStateException("茶友圈通知消息消费失败", e);
        }
    }
}
