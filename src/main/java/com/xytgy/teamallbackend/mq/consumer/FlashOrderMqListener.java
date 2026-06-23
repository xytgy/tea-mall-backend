package com.xytgy.teamallbackend.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xytgy.teamallbackend.mq.constant.MqConstants;
import com.xytgy.teamallbackend.mq.handler.FlashOrderHandler;
import com.xytgy.teamallbackend.mq.message.flashsale.FlashOrderCreateMessage;
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
        topic = MqConstants.TOPIC_FLASH_ORDER,
        consumerGroup = MqConstants.GROUP_FLASH_ORDER
)
public class FlashOrderMqListener implements RocketMQListener<String> {

    private final ObjectMapper objectMapper;
    private final FlashOrderHandler flashOrderHandler;

    @Override
    public void onMessage(String body) {
        try {
            FlashOrderCreateMessage message = objectMapper.readValue(body, FlashOrderCreateMessage.class);
            validate(message);
            flashOrderHandler.handle(message);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("秒杀订单消息消费失败, body={}", body, e);
            throw new IllegalStateException("秒杀订单消息消费失败", e);
        }
    }

    private void validate(FlashOrderCreateMessage message) {
        if (message.getTransactionId() == null
                || message.getUserId() == null
                || message.getFlashSaleId() == null
                || message.getProductId() == null
                || message.getFlashPrice() == null) {
            throw new IllegalArgumentException("秒杀订单消息字段不完整");
        }
    }
}
