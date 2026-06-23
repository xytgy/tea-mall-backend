package com.xytgy.teamallbackend.mq.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PaymentNotifyHandler {

    public void handle(Long orderId, Long userId, Long paymentId) {
        log.info("支付成功异步通知: orderId={}, userId={}, paymentId={}", orderId, userId, paymentId);
    }
}
