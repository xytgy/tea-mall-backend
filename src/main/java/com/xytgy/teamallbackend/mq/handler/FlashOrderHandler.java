package com.xytgy.teamallbackend.mq.handler;

import com.xytgy.teamallbackend.mq.message.flashsale.FlashOrderCreateMessage;
import com.xytgy.teamallbackend.module.flashsale.service.FlashOrderPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlashOrderHandler {

    private final FlashOrderPersistenceService flashOrderPersistenceService;

    public void handle(FlashOrderCreateMessage message) {
        try {
            flashOrderPersistenceService.createFlashOrder(message);
        } catch (Exception e) {
            log.error("秒杀订单处理失败, transactionId={}", message.getTransactionId(), e);
            flashOrderPersistenceService.recordFailure(message, e);
            throw e;
        }
    }
}
