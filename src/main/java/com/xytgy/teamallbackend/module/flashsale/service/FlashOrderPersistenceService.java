package com.xytgy.teamallbackend.module.flashsale.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.xytgy.teamallbackend.mq.message.flashsale.FlashOrderCreateMessage;
import com.xytgy.teamallbackend.module.flashsale.entity.FlashSaleFailedOrder;
import com.xytgy.teamallbackend.module.flashsale.mapper.FlashSaleFailedOrderMapper;
import com.xytgy.teamallbackend.module.order.entity.Orders;
import com.xytgy.teamallbackend.module.order.mapper.OrdersMapper;
import com.xytgy.teamallbackend.module.product.entity.Product;
import com.xytgy.teamallbackend.module.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlashOrderPersistenceService {

    private static final String FLASH_PENDING_PREFIX = "flash:pending:";

    private final OrdersMapper ordersMapper;
    private final ProductMapper productMapper;
    private final FlashSaleFailedOrderMapper failedOrderMapper;
    private final FlashSaleMetrics metrics;
    private final FlashSaleNotificationService notificationService;
    private final StringRedisTemplate stringRedisTemplate;

    @Transactional(rollbackFor = Exception.class)
    public boolean createFlashOrder(FlashOrderCreateMessage message) {
        if (alreadyCreated(message.getTransactionId())) {
            clearPending(message.getTransactionId());
            log.info("重复消费, 跳过: transactionId={}", message.getTransactionId());
            return false;
        }

        Orders order = buildOrder(message);
        try {
            ordersMapper.insert(order);
        } catch (DuplicateKeyException duplicateKeyException) {
            clearPending(message.getTransactionId());
            log.info("秒杀订单已存在, 跳过重复创建: transactionId={}", message.getTransactionId());
            return false;
        }

        int affected = productMapper.update(null, new UpdateWrapper<Product>()
                .eq("id", message.getProductId())
                .gt("stock", 0)
                .setSql("stock = stock - 1"));
        if (affected != 1) {
            throw new IllegalStateException("秒杀订单创建成功但数据库库存扣减失败, transactionId="
                    + message.getTransactionId());
        }

        clearPending(message.getTransactionId());
        metrics.increment("flash.order.create.success");
        metrics.increment("flash.order.create.total");
        return true;
    }

    public void recordFailure(FlashOrderCreateMessage message, Exception e) {
        metrics.increment("flash.order.create.fail");
        metrics.increment("flash.order.create.total");
        try {
            FlashSaleFailedOrder failed = new FlashSaleFailedOrder();
            failed.setTransactionId(message.getTransactionId());
            failed.setUserId(message.getUserId());
            failed.setProductId(message.getProductId());
            failed.setFlashSaleId(message.getFlashSaleId());
            failed.setErrorMsg(e.getMessage());
            failed.setRetryCount(0);
            failed.setStatus(0);
            failed.setCreateTime(LocalDateTime.now());
            failedOrderMapper.insert(failed);
            notificationService.sendFailureNotification(
                    message.getUserId(),
                    message.getFlashSaleId(),
                    message.getProductId(),
                    e.getMessage()
            );
        } catch (Exception ex) {
            log.error("秒杀失败订单记录入库也失败, transactionId={}", message.getTransactionId(), ex);
        }
    }

    private boolean alreadyCreated(String transactionId) {
        Long existCount = ordersMapper.selectCount(
                new LambdaQueryWrapper<Orders>().eq(Orders::getOrderNo, transactionId)
        );
        return existCount != null && existCount > 0;
    }

    private Orders buildOrder(FlashOrderCreateMessage message) {
        Orders order = new Orders();
        order.setOrderNo(message.getTransactionId());
        order.setUserId(message.getUserId());
        order.setTotalAmount(message.getFlashPrice());
        order.setStatus(Orders.STATUS_PENDING_PAYMENT);
        order.setSource(Orders.SOURCE_FLASH_SALE);
        order.setCreateTime(LocalDateTime.now());
        return order;
    }

    private void clearPending(String transactionId) {
        stringRedisTemplate.delete(FLASH_PENDING_PREFIX + transactionId);
    }
}
