package com.xytgy.teamallbackend.config.mq;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xytgy.teamallbackend.module.flashsale.entity.FlashSaleFailedOrder;
import com.xytgy.teamallbackend.module.flashsale.repository.FlashSaleFailedOrderMapper;
import com.xytgy.teamallbackend.module.flashsale.service.FlashSaleMetrics;
import com.xytgy.teamallbackend.module.flashsale.service.FlashSaleNotificationService;
import com.xytgy.teamallbackend.module.order.entity.Orders;
import com.xytgy.teamallbackend.module.order.repository.OrdersMapper;
import com.xytgy.teamallbackend.module.product.entity.Product;
import com.xytgy.teamallbackend.module.product.repository.ProductMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀订单 MQ 消费者。
 * <p>
 * 接收秒杀下单消息后先入缓冲队列，后台线程按批次落库，
 * 单条失败不影响其他消息，失败消息写入 flash_sale_failed_order 表。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstants.TOPIC_FLASH_ORDER,
        consumerGroup = "tea-mall-flash-order-group"
)
public class FlashOrderConsumer implements RocketMQListener<MessageExt> {

    private final ObjectMapper objectMapper;
    private final OrdersMapper ordersMapper;
    private final ProductMapper productMapper;
    private final FlashSaleFailedOrderMapper failedOrderMapper;
    private final FlashSaleMetrics metrics;
    private final TransactionTemplate transactionTemplate;
    private final FlashSaleNotificationService notificationService;
    private final org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Value("${flash-sale.consumer.batch-size:50}")
    private int batchSize;

    @Value("${flash-sale.consumer.poll-interval-ms:100}")
    private long pollIntervalMs;

    @Value("${flash-sale.consumer.thread-count:4}")
    private int consumerThreadCount;

    /** 消息缓冲队列，防止瞬时流量压垮数据库 */
    private final BlockingQueue<MessageExt> messageQueue = new ArrayBlockingQueue<>(10000);

    // 多线程消费：单线程 500 条/秒 → 4 线程 2000 条/秒
    private final List<Thread> consumerThreads = new ArrayList<>();
    private volatile boolean running = true;

    @PostConstruct
    public void start() {
        for (int i = 0; i < consumerThreadCount; i++) {
            Thread t = new Thread(this::processLoop, "flash-order-consumer-" + i);
            t.setDaemon(true);
            t.start();
            consumerThreads.add(t);
        }
        log.info("秒杀订单消费线程已启动, 线程数={}, batchSize={}, pollIntervalMs={}",
                consumerThreadCount, batchSize, pollIntervalMs);
    }

    @PreDestroy
    public void stop() {
        running = false;
        // P0#2: 等待消费线程处理完当前批次（最多 10 秒），避免事务中断导致数据丢失
        for (Thread t : consumerThreads) {
            t.interrupt();
        }
        for (Thread t : consumerThreads) {
            try {
                t.join(10_000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("秒杀订单消费线程已全部停止, 队列残留消息数={}", messageQueue.size());
    }

    @Override
    public void onMessage(MessageExt messageExt) {
        // P0#3: 改用 put() 阻塞等待，让 RocketMQ 消费背压自然传导
        // 原来用 offer() 队列满时直接丢弃消息，导致库存已扣但订单丢失
        try {
            messageQueue.put(messageExt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("消息入队被中断, msgId={}", messageExt.getMsgId());
        }
    }

    private void processLoop() {
        while (running) {
            try {
                List<MessageExt> batch = new ArrayList<>(batchSize);
                messageQueue.drainTo(batch, batchSize);
                if (batch.isEmpty()) {
                    TimeUnit.MILLISECONDS.sleep(pollIntervalMs);
                    continue;
                }
                processBatch(batch);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("秒杀订单消费线程被中断");
            } catch (Exception e) {
                log.error("秒杀订单批量处理异常", e);
            }
        }
    }

    private void processBatch(List<MessageExt> batch) {
        List<Long> successProductIds = new ArrayList<>();
        for (MessageExt msg : batch) {
            try {
                processSingleMessage(msg, successProductIds);
            } catch (Exception e) {
                log.error("秒杀订单单条处理异常, msgId={}", msg.getMsgId(), e);
            }
        }
    }

    private void processSingleMessage(MessageExt msg, List<Long> successProductIds) throws Exception {
        String body = new String(msg.getBody(), StandardCharsets.UTF_8);
        JsonNode json = objectMapper.readTree(body);

        String transactionId = json.get("transactionId").asText();
        Long userId = json.get("userId").asLong();
        Long flashSaleId = json.get("flashSaleId").asLong();
        Long productId = json.get("productId").asLong();
        BigDecimal flashPrice = json.get("flashPrice").decimalValue();

        // P0#4: 幂等检查 — 同一 transactionId 不重复创建订单（RocketMQ 至少一次语义）
        Long existCount = ordersMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Orders>()
                        .eq(Orders::getOrderNo, transactionId));
        if (existCount != null && existCount > 0) {
            log.info("重复消费, 跳过: transactionId={}", transactionId);
            stringRedisTemplate.delete("flash:pending:" + transactionId);
            return;
        }

        Orders order = new Orders();
        order.setOrderNo(transactionId);
        order.setUserId(userId);
        order.setTotalAmount(flashPrice);
        order.setStatus(Orders.STATUS_PENDING_PAYMENT);
        order.setSource(Orders.SOURCE_FLASH_SALE);
        order.setCreateTime(LocalDateTime.now());

        // P0#4: 库存扣减加乐观锁 WHERE stock > 0，防止 DB 库存变负数
        try {
            ordersMapper.insert(order);
            productMapper.update(null, new UpdateWrapper<Product>()
                    .eq("id", productId)
                    .gt("stock", 0)
                    .setSql("stock = stock - 1"));
            successProductIds.add(productId);
            metrics.increment("flash.order.create.success");
            stringRedisTemplate.delete("flash:pending:" + transactionId);
        } catch (Exception e) {
            metrics.increment("flash.order.create.fail");
            log.error("秒杀订单入库失败, transactionId={}, 尝试自动重试", transactionId, e);
            if (retryInsert(order, transactionId, userId, productId, flashSaleId)) {
                successProductIds.add(productId);
                metrics.increment("flash.order.create.success");
                stringRedisTemplate.delete("flash:pending:" + transactionId);
            }
        }
        metrics.increment("flash.order.create.total");
    }

    private void writeFailedOrder(String transactionId, Long userId, Long productId,
                                  Long flashSaleId, Exception e) {
        try {
            FlashSaleFailedOrder failed = new FlashSaleFailedOrder();
            failed.setTransactionId(transactionId);
            failed.setUserId(userId);
            failed.setProductId(productId);
            failed.setFlashSaleId(flashSaleId);
            failed.setErrorMsg(e.getMessage());
            failed.setRetryCount(0);
            failed.setStatus(0);
            failed.setCreateTime(LocalDateTime.now());
            failedOrderMapper.insert(failed);
            // 三级降级通知用户
            notificationService.sendFailureNotification(userId, flashSaleId, productId, e.getMessage());
        } catch (Exception ex) {
            log.error("秒杀失败订单记录入库也失败, transactionId={}", transactionId, ex);
        }
    }

    /**
     * 补偿策略 P0：自动重试 1 次，直接重新 INSERT。
     * 重试也失败则写入死信表。
     */
    private boolean retryInsert(Orders original, String transactionId,
                                Long userId, Long productId, Long flashSaleId) {
        try {
            Orders retryOrder = new Orders();
            retryOrder.setOrderNo("FS" + UUID.randomUUID().toString().replace("-", ""));
            retryOrder.setUserId(original.getUserId());
            retryOrder.setTotalAmount(original.getTotalAmount());
            retryOrder.setStatus(Orders.STATUS_PENDING_PAYMENT);
            retryOrder.setSource(Orders.SOURCE_FLASH_SALE);
            retryOrder.setCreateTime(LocalDateTime.now());
            transactionTemplate.executeWithoutResult(status -> {
                ordersMapper.insert(retryOrder);
                productMapper.update(null, new UpdateWrapper<Product>()
                        .eq("id", productId)
                        .setSql("stock = stock - 1"));
            });
            return true;
        } catch (Exception retryEx) {
            log.error("自动重试也失败, 转入死信队列, transactionId={}", transactionId, retryEx);
            writeFailedOrder(transactionId, userId, productId, flashSaleId, retryEx);
            return false;
        }
    }
}
