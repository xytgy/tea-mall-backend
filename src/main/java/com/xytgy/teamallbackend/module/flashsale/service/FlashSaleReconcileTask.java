package com.xytgy.teamallbackend.module.flashsale.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xytgy.teamallbackend.config.mq.FlashSaleCacheManager;
import com.xytgy.teamallbackend.module.flashsale.entity.FlashSale;
import com.xytgy.teamallbackend.module.flashsale.entity.FlashSaleProduct;
import com.xytgy.teamallbackend.module.flashsale.repository.FlashSaleMapper;
import com.xytgy.teamallbackend.module.flashsale.repository.FlashSaleProductMapper;
import com.xytgy.teamallbackend.module.product.entity.Product;
import com.xytgy.teamallbackend.module.product.repository.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀对账 & 自动清理定时任务。
 * <p>
 * 任务 1（每 60 秒）：对比 Redis 与 MySQL 库存，发现不一致时以 MySQL 为准回补 Redis。
 * 任务 2（每小时）：清理已超时未手动结束的活动，设置 Redis key TTL。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FlashSaleReconcileTask {

    private final StringRedisTemplate stringRedisTemplate;
    private final FlashSaleMapper flashSaleMapper;
    private final FlashSaleProductMapper flashSaleProductMapper;
    private final ProductMapper productMapper;
    private final FlashSaleCacheManager cacheManager;
    private final FlashSaleMetrics metrics;

    private static final String STOCK_KEY_PREFIX = "{flash:";
    private static final String STOCK_KEY_SUFFIX = "}:stock";
    private static final String BOUGHT_KEY_PREFIX = "{flash:";
    private static final String BOUGHT_KEY_SUFFIX = "}:bought:";

    /** 连续异常计数，达到阈值触发告警 */
    // P2#12: 使用 AtomicInteger 保证线程安全
    private final java.util.concurrent.atomic.AtomicInteger consecutiveAnomalyCount = new java.util.concurrent.atomic.AtomicInteger(0);

    /**
     * 对账任务：每 60 秒检查已结束活动的 Redis/MySQL 库存一致性
     */
    @Scheduled(fixedRate = 60000)
    public void reconcile() {
        // P1#6: 只查已结束的活动（status=0），不再查进行中的，避免每 60 秒白查一遍
        List<FlashSale> sales = flashSaleMapper.selectList(
                new LambdaQueryWrapper<FlashSale>()
                        .eq(FlashSale::getStatus, 0));

        int anomalyCount = 0;
        for (FlashSale sale : sales) {

            List<FlashSaleProduct> products = flashSaleProductMapper.selectList(
                    new LambdaQueryWrapper<FlashSaleProduct>()
                            .eq(FlashSaleProduct::getFlashSaleId, sale.getId()));

            for (FlashSaleProduct fsp : products) {
                anomalyCount += checkStockConsistency(fsp);
            }
        }

        updateAnomalyAlert(anomalyCount);
    }

    /**
     * 检查单个商品的 Redis 与 MySQL 库存一致性，不一致时以 MySQL 为准回补 Redis
     * @return 异常计数（0 或 1）
     */
    private int checkStockConsistency(FlashSaleProduct fsp) {
        String stockKey = STOCK_KEY_PREFIX + fsp.getProductId() + STOCK_KEY_SUFFIX;
        String redisStockStr = stringRedisTemplate.opsForValue().get(stockKey);
        if (redisStockStr == null) {
            return 0;
        }

        int redisStock = Integer.parseInt(redisStockStr);
        Product product = productMapper.selectById(fsp.getProductId());
        if (product == null) {
            return 0;
        }

        int dbStock = product.getStock();
        if (redisStock == dbStock) {
            return 0;
        }

        metrics.increment("flash.reconcile.anomaly");

        if (redisStock > dbStock) {
            // 超卖：以 MySQL 为准回补 Redis
            log.error("对账：超卖 productId={}, redis={}, db={}", fsp.getProductId(), redisStock, dbStock);
            stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(dbStock));
            cacheManager.syncLocalStock(fsp.getProductId(), dbStock);
        } else {
            // Redis 偏少（可能回补未同步），以 Redis 为准
            log.warn("对账：Redis 库存偏少 productId={}, redis={}, db={}", fsp.getProductId(), redisStock, dbStock);
        }
        return 1;
    }

    /**
     * 连续异常告警：连续 3 次对账发现异常时触发告警
     */
    private void updateAnomalyAlert(int anomalyCount) {
        if (anomalyCount > 0) {
            int count = consecutiveAnomalyCount.incrementAndGet();
            if (count >= 3) {
                log.error("[秒杀对账告警] 连续 {} 次对账发现异常，共 {} 个不一致", count, anomalyCount);
            }
        } else {
            consecutiveAnomalyCount.set(0);
        }
    }

    /**
     * 自动清理任务：每小时检查超时未手动结束的活动，设置 Redis key TTL
     */
    @Scheduled(fixedRate = 3600000)
    public void autoCleanup() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);
        List<FlashSale> expiredSales = flashSaleMapper.selectList(
                new LambdaQueryWrapper<FlashSale>()
                        .eq(FlashSale::getStatus, 1)
                        .lt(FlashSale::getEndTime, threshold));

        for (FlashSale sale : expiredSales) {
            try {
                cleanupExpiredSale(sale);
            } catch (Exception e) {
                log.error("[自动清理] 活动 {} 清理失败", sale.getId(), e);
            }
        }
    }

    /**
     * 对单个超期活动设置 Redis key TTL 并标记为已结束
     */
    private void cleanupExpiredSale(FlashSale sale) {
        List<FlashSaleProduct> products = flashSaleProductMapper.selectList(
                new LambdaQueryWrapper<FlashSaleProduct>()
                        .eq(FlashSaleProduct::getFlashSaleId, sale.getId()));

        for (FlashSaleProduct fp : products) {
            String stockKey = STOCK_KEY_PREFIX + fp.getProductId() + STOCK_KEY_SUFFIX;
            String boughtKey = BOUGHT_KEY_PREFIX + fp.getProductId() + BOUGHT_KEY_SUFFIX + sale.getId();
            // stock 保留 24h，bought 保留 7d，给予对账和申诉窗口
            stringRedisTemplate.expire(stockKey, 24, TimeUnit.HOURS);
            stringRedisTemplate.expire(boughtKey, 7, TimeUnit.DAYS);
            cacheManager.clear(fp.getProductId());
        }

        sale.setStatus(2);
        flashSaleMapper.updateById(sale);
        log.info("[自动清理] 活动 {} 的 Redis key 已设置 TTL", sale.getId());
    }
}
