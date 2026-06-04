package com.xytgy.teamallbackend.config.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstants.TOPIC_PAYMENT_NOTIFY,
        consumerGroup = "tea-mall-payment-notify-group",
        selectorExpression = MqConstants.TAG_PAY_SUCCESS
)
public class PaymentNotifyConsumer implements RocketMQListener<String> {

    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String body) {
        try {
            JsonNode json = objectMapper.readTree(body);
            Long orderId = json.get("orderId").asLong();
            Long userId = json.get("userId").asLong();
            Long paymentId = json.get("paymentId").asLong();

            log.info("支付成功异步通知: orderId={}, userId={}, paymentId={}", orderId, userId, paymentId);

            // 预留扩展点：站内信、短信通知、邮件通知、WebSocket 推送等
        } catch (Exception e) {
            log.error("支付通知处理异常, body={}", body, e);
        }
    }
}
