package com.xytgy.teamallbackend.mq.publisher;

import com.xytgy.teamallbackend.mq.constant.MqConstants;
import com.xytgy.teamallbackend.mq.message.flashsale.FlashOrderCreateMessage;
import com.xytgy.teamallbackend.mq.producer.MqProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlashOrderPublisher {

    private final ObjectProvider<MqProducer> mqProducerProvider;

    public boolean publishCreateOrder(FlashOrderCreateMessage message) {
        MqProducer mqProducer = mqProducerProvider.getIfAvailable();
        if (mqProducer == null) {
            log.warn("RocketMQ 不可用，秒杀下单消息未发送: transactionId={}", message.getTransactionId());
            return false;
        }

        mqProducer.send(
                MqConstants.TOPIC_FLASH_ORDER,
                MqConstants.TAG_FLASH_ORDER,
                message.getTransactionId(),
                message
        );
        return true;
    }
}
