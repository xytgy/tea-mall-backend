package com.xytgy.teamallbackend.mq.consumer;
import com.xytgy.teamallbackend.mq.constant.MqConstants;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xytgy.teamallbackend.mq.handler.OrderTimeoutHandler;
import com.xytgy.teamallbackend.mq.message.order.OrderTimeoutMessage;
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
        topic = MqConstants.TOPIC_ORDER_TIMEOUT,
        consumerGroup = MqConstants.GROUP_ORDER_TIMEOUT,
        selectorExpression = MqConstants.TAG_TIMEOUT_CANCEL
)
public class OrderTimeoutConsumer implements RocketMQListener<String> {

    private final ObjectMapper objectMapper;
    private final OrderTimeoutHandler orderTimeoutHandler;
    private final IdempotentUtil idempotentUtil;

    @Override
    public void onMessage(String body) {
        try {
            OrderTimeoutMessage message = objectMapper.readValue(body, OrderTimeoutMessage.class);
            if (message.getOrderId() == null) {
                throw new IllegalArgumentException("订单超时消息缺少 orderId");
            }
            // 幂等检查
            String messageId = "order-timeout:" + message.getOrderId();
            if (!idempotentUtil.tryConsume(messageId, MqConstants.GROUP_ORDER_TIMEOUT, MqConstants.TOPIC_ORDER_TIMEOUT)) {
                log.debug("订单超时消息已消费过, orderId={}", message.getOrderId());
                return;
            }
            orderTimeoutHandler.handle(message.getOrderId());
        } catch (Exception e) {
            log.error("订单超时处理异常, body={}", body, e);
            throw new IllegalStateException("订单超时消息消费失败", e);
        }
    }
}
