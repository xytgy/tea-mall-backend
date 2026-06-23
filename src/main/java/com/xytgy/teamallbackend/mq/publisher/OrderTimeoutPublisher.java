package com.xytgy.teamallbackend.mq.publisher;

import com.xytgy.teamallbackend.mq.constant.MqConstants;
import com.xytgy.teamallbackend.mq.message.order.OrderTimeoutMessage;
import com.xytgy.teamallbackend.mq.producer.MqProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutPublisher {

    private final ObjectProvider<MqProducer> mqProducerProvider;

    public boolean publishOrderTimeout(OrderTimeoutMessage message) {
        MqProducer mqProducer = mqProducerProvider.getIfAvailable();
        if (mqProducer == null) {
            log.warn("RocketMQ 不可用，订单超时消息未发送: orderId={}", message.getOrderId());
            return false;
        }
        mqProducer.sendDelay(
                MqConstants.TOPIC_ORDER_TIMEOUT,
                MqConstants.TAG_TIMEOUT_CANCEL,
                String.valueOf(message.getOrderId()),
                message,
                MqConstants.DELAY_LEVEL_30_MINUTES
        );
        return true;
    }
}
