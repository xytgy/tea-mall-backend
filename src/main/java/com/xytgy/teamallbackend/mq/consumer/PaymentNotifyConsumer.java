package com.xytgy.teamallbackend.mq.consumer;
import com.xytgy.teamallbackend.mq.constant.MqConstants;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xytgy.teamallbackend.mq.handler.PaymentNotifyHandler;
import com.xytgy.teamallbackend.mq.message.order.PaymentSuccessMessage;
import com.xytgy.teamallbackend.mq.util.IdempotentUtil;
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
        topic = MqConstants.TOPIC_PAYMENT_NOTIFY,
        consumerGroup = MqConstants.GROUP_PAYMENT_NOTIFY,
        selectorExpression = MqConstants.TAG_PAY_SUCCESS
)
public class PaymentNotifyConsumer implements RocketMQListener<String> {

    private final ObjectMapper objectMapper;
    private final PaymentNotifyHandler paymentNotifyHandler;
    private final IdempotentUtil idempotentUtil;

    @Override
    public void onMessage(String body) {
        try {
            PaymentSuccessMessage message = objectMapper.readValue(body, PaymentSuccessMessage.class);
            if (message.getOrderId() == null || message.getUserId() == null || message.getPaymentId() == null) {
                throw new IllegalArgumentException("支付成功消息字段不完整");
            }
            // 幂等检查
            String messageId = "payment:" + message.getOrderId();
            if (!idempotentUtil.tryConsume(messageId, MqConstants.GROUP_PAYMENT_NOTIFY, MqConstants.TOPIC_PAYMENT_NOTIFY)) {
                log.debug("支付通知已消费过, orderId={}", message.getOrderId());
                return;
            }
            paymentNotifyHandler.handle(message.getOrderId(), message.getUserId(), message.getPaymentId());
        } catch (Exception e) {
            log.error("支付通知处理异常, body={}", body, e);
            throw new IllegalStateException("支付通知消息消费失败", e);
        }
    }
}
