package com.xytgy.teamallbackend.module.flashsale.service;

import com.xytgy.teamallbackend.mq.message.flashsale.FlashOrderCreateMessage;
import com.xytgy.teamallbackend.module.flashsale.mapper.FlashSaleFailedOrderMapper;
import com.xytgy.teamallbackend.module.order.entity.Orders;
import com.xytgy.teamallbackend.module.order.mapper.OrdersMapper;
import com.xytgy.teamallbackend.module.product.mapper.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlashOrderPersistenceServiceTest {

    @Mock
    private OrdersMapper ordersMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private FlashSaleFailedOrderMapper failedOrderMapper;
    @Mock
    private FlashSaleMetrics metrics;
    @Mock
    private FlashSaleNotificationService notificationService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private FlashOrderPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new FlashOrderPersistenceService(
                ordersMapper,
                productMapper,
                failedOrderMapper,
                metrics,
                notificationService,
                stringRedisTemplate
        );
    }

    @Test
    void createsFlashOrderAndClearsPendingOnSuccess() {
        FlashOrderCreateMessage message = buildMessage();
        when(ordersMapper.selectCount(any())).thenReturn(0L);
        when(productMapper.update(any(), any())).thenReturn(1);

        boolean created = service.createFlashOrder(message);

        assertTrue(created);
        ArgumentCaptor<Orders> orderCaptor = ArgumentCaptor.forClass(Orders.class);
        verify(ordersMapper).insert(orderCaptor.capture());
        Orders saved = orderCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(message.getTransactionId(), saved.getOrderNo());
        org.junit.jupiter.api.Assertions.assertEquals(message.getUserId(), saved.getUserId());
        org.junit.jupiter.api.Assertions.assertEquals(Orders.SOURCE_FLASH_SALE, saved.getSource());
        verify(stringRedisTemplate).delete("flash:pending:" + message.getTransactionId());
        verify(metrics).increment("flash.order.create.success");
        verify(metrics).increment("flash.order.create.total");
    }

    @Test
    void skipsDuplicateFlashOrderAndDoesNotInsertAgain() {
        FlashOrderCreateMessage message = buildMessage();
        when(ordersMapper.selectCount(any())).thenReturn(1L);

        boolean created = service.createFlashOrder(message);

        assertFalse(created);
        verify(ordersMapper, never()).insert(any(Orders.class));
        verify(productMapper, never()).update(any(), any());
        verify(stringRedisTemplate).delete("flash:pending:" + message.getTransactionId());
        verify(metrics, never()).increment(eq("flash.order.create.success"));
    }

    private FlashOrderCreateMessage buildMessage() {
        return FlashOrderCreateMessage.builder()
                .transactionId("flash:1:2:3:123456")
                .flashSaleId(1L)
                .productId(2L)
                .userId(3L)
                .flashPrice(new BigDecimal("88.00"))
                .build();
    }
}
