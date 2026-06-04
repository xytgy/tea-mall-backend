package com.xytgy.teamallbackend.config.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xytgy.teamallbackend.module.order.entity.Orders;
import com.xytgy.teamallbackend.module.order.service.OrdersService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstants.TOPIC_ORDER_TIMEOUT,
        consumerGroup = "tea-mall-order-timeout-group",
        selectorExpression = MqConstants.TAG_TIMEOUT_CANCEL
)
public class OrderTimeoutConsumer implements RocketMQListener<String> {

    private final OrdersService ordersService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String body) {
        try {
            JsonNode json = objectMapper.readTree(body);
            Long orderId = json.get("orderId").asLong();
            Long userId = json.get("userId").asLong();

            Orders order = ordersService.getById(orderId);
            if (order == null) {
                log.warn("订单超时处理：订单不存在, orderId={}", orderId);
                return;
            }

            // 已支付或已取消，无需处理
            if (!Objects.equals(order.getStatus(), 0)) {
                log.info("订单超时处理：订单状态已变更, orderId={}, status={}", orderId, order.getStatus());
                return;
            }

            // 通过自注入代理调用事务方法，确保事务生效
            ordersService.doCancelOrderInTransaction(order);
            log.info("订单超时自动取消成功, orderId={}", orderId);
        } catch (Exception e) {
            log.error("订单超时处理异常, body={}", body, e);
        }
    }
}
