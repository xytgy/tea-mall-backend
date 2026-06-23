package com.xytgy.teamallbackend.mq.publisher;

import com.xytgy.teamallbackend.mq.constant.MqConstants;
import com.xytgy.teamallbackend.mq.message.order.PaymentSuccessMessage;
import com.xytgy.teamallbackend.mq.producer.MqProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private final ObjectProvider<MqProducer> mqProducerProvider;

    public boolean publishPaymentSuccess(PaymentSuccessMessage message) {
        MqProducer mqProducer = mqProducerProvider.getIfAvailable();
        if (mqProducer == null) {
            log.warn("RocketMQ 不可用，支付成功消息未发送: orderId={}", message.getOrderId());
            return false;
        }
        mqProducer.send(
                MqConstants.TOPIC_PAYMENT_NOTIFY,
                MqConstants.TAG_PAY_SUCCESS,
                String.valueOf(message.getOrderId()),
                message
        );
        return true;
    }
}
