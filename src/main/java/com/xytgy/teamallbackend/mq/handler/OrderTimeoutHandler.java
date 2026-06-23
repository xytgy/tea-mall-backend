package com.xytgy.teamallbackend.mq.handler;

import com.xytgy.teamallbackend.module.order.entity.Orders;
import com.xytgy.teamallbackend.module.order.service.OrdersService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutHandler {

    private final OrdersService ordersService;

    public void handle(Long orderId) {
        Orders order = ordersService.getById(orderId);
        if (order == null) {
            log.warn("订单超时处理：订单不存在, orderId={}", orderId);
            return;
        }
        if (!Objects.equals(order.getStatus(), Orders.STATUS_PENDING_PAYMENT)) {
            log.info("订单超时处理：订单状态已变更, orderId={}, status={}", orderId, order.getStatus());
            return;
        }
        ordersService.doCancelOrderInTransaction(order);
        log.info("订单超时自动取消成功, orderId={}", orderId);
    }
}
