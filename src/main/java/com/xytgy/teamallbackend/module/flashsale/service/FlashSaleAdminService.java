package com.xytgy.teamallbackend.module.flashsale.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.config.mq.FlashSaleCacheManager;
import com.xytgy.teamallbackend.config.mq.MqConstants;
import com.xytgy.teamallbackend.config.mq.MqProducer;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.flashsale.entity.FlashSale;
import com.xytgy.teamallbackend.module.flashsale.entity.FlashSaleAuditLog;
import com.xytgy.teamallbackend.module.flashsale.entity.FlashSaleCompensation;
import com.xytgy.teamallbackend.module.flashsale.entity.FlashSaleFailedOrder;
import com.xytgy.teamallbackend.module.flashsale.entity.FlashSaleProduct;
import com.xytgy.teamallbackend.module.flashsale.entity.FlashSaleWhitelist;
import com.xytgy.teamallbackend.module.flashsale.repository.FlashSaleAuditLogMapper;
import com.xytgy.teamallbackend.module.flashsale.repository.FlashSaleCompensationMapper;
import com.xytgy.teamallbackend.module.flashsale.repository.FlashSaleFailedOrderMapper;
import com.xytgy.teamallbackend.module.flashsale.repository.FlashSaleMapper;
import com.xytgy.teamallbackend.module.flashsale.repository.FlashSaleProductMapper;
import com.xytgy.teamallbackend.module.flashsale.repository.FlashSaleWhitelistMapper;
import com.xytgy.teamallbackend.module.flashsale.service.FlashSaleService.FlashSaleBuyResult;
import com.xytgy.teamallbackend.module.flashsale.vo.FlashSaleProductVO;
import com.xytgy.teamallbackend.module.flashsale.vo.FlashSaleVO;
import com.xytgy.teamallbackend.module.order.entity.Orders;
import com.xytgy.teamallbackend.module.order.repository.OrdersMapper;
import com.xytgy.teamallbackend.module.product.entity.Product;
import com.xytgy.teamallbackend.module.product.repository.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class FlashSaleAdminService {

    private final StringRedisTemplate stringRedisTemplate;
    private final FlashSaleCacheManager cacheManager;
    private final FlashSaleMapper flashSaleMapper;
    private final FlashSaleProductMapper flashSaleProductMapper;
    private final FlashSaleWhitelistMapper flashSaleWhitelistMapper;
    private final FlashSaleAuditLogMapper flashSaleAuditLogMapper;
    private final ProductMapper productMapper;
    private final OrdersMapper ordersMapper;
    private final MqProducer mqProducer;
    private final FlashSaleRateLimiter rateLimiter;
    private final FlashSaleFailedOrderMapper failedOrderMapper;
    private final FlashSaleCompensationMapper compensationMapper;

    private static final String FLASH_STOCK_PREFIX = "{flash:";
    private static final String FLASH_PENDING_PREFIX = "flash:pending:";

    public List<FlashSaleVO> listActiveSales() {
        LocalDateTime now = LocalDateTime.now();
        List<FlashSale> sales = flashSaleMapper.selectList(
                new LambdaQueryWrapper<FlashSale>()
                        .eq(FlashSale::getStatus, 1)
                        .le(FlashSale::getStartTime, now)
                        .ge(FlashSale::getEndTime, now)
        );
        return sales.stream().map(this::toFlashSaleVO).toList();
    }

    public List<FlashSaleProductVO> getProducts(Long flashSaleId) {
        List<FlashSaleProduct> products = flashSaleProductMapper.selectList(
                new LambdaQueryWrapper<FlashSaleProduct>()
                        .eq(FlashSaleProduct::getFlashSaleId, flashSaleId)
        );
        if (products.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> productIds = products.stream().map(FlashSaleProduct::getProductId).toList();
        Map<Long, Product> productMap = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        return products.stream().map(fp -> buildProductVO(fp, productMap.get(fp.getProductId()))).toList();
    }

    public Map<String, Object> getOrderResult(Long userId, Long orderId) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (orderId != null) {
            Orders order = ordersMapper.selectById(orderId);
            if (order != null && order.getUserId().equals(userId)) {
                result.put("orderId", order.getId());
                result.put("orderNo", order.getOrderNo());
                result.put("status", order.getStatus());
                result.put("message", getOrderStatusMessage(order.getStatus()));
                return result;
            }
            result.put("message", "订单不存在");
            return result;
        }

        Set<String> keys = stringRedisTemplate.keys(FLASH_PENDING_PREFIX + userId + ":*");
        if (keys != null && !keys.isEmpty()) {
            for (String key : keys) {
                String pendingOrderId = stringRedisTemplate.opsForValue().get(key);
                if (pendingOrderId != null) {
                    result.put("orderId", Long.parseLong(pendingOrderId));
                    result.put("status", "PENDING");
                    result.put("message", "订单处理中");
                    return result;
                }
            }
        }

        result.put("message", "暂无抢购记录");
        return result;
    }

    public FlashSaleBuyResult appeal(Long userId, Long flashSaleId) {
        FlashSale flashSale = flashSaleMapper.selectById(flashSaleId);
        if (flashSale == null) {
            return fail("活动不存在");
        }

        // 查找该用户在此活动下的失败订单
        List<FlashSaleFailedOrder> failedOrders = failedOrderMapper.selectList(
                new LambdaQueryWrapper<FlashSaleFailedOrder>()
                        .eq(FlashSaleFailedOrder::getUserId, userId)
                        .eq(FlashSaleFailedOrder::getFlashSaleId, flashSaleId)
                        .eq(FlashSaleFailedOrder::getStatus, 0));

        if (failedOrders.isEmpty()) {
            return fail("暂无失败订单，无需申诉");
        }

        // 标记为申诉中（status=2），等待人工或自动处理
        for (FlashSaleFailedOrder fo : failedOrders) {
            fo.setStatus(2);
            failedOrderMapper.updateById(fo);
        }

        saveAuditLog(flashSaleId, userId, "APPEAL", "用户 " + userId + " 申诉 " + failedOrders.size() + " 笔失败订单");
        log.info("秒杀申诉提交: userId={}, flashSaleId={}, 失败订单数={}", userId, flashSaleId, failedOrders.size());
        return new FlashSaleBuyResult("SUCCESS", null, "申诉已提交，共 " + failedOrders.size() + " 笔订单");
    }

    public Map<String, Object> getFailedOrders(int page, int size) {
        Page<FlashSaleFailedOrder> pageParam = new Page<>(page, size);
        Page<FlashSaleFailedOrder> result = failedOrderMapper.selectPage(pageParam,
                new LambdaQueryWrapper<FlashSaleFailedOrder>()
                        .orderByDesc(FlashSaleFailedOrder::getCreateTime));

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("total", result.getTotal());
        map.put("records", result.getRecords());
        return map;
    }

    public void retryFailedOrder(Long failedOrderId, Long operatorId) {
        FlashSaleFailedOrder failedOrder = failedOrderMapper.selectById(failedOrderId);
        if (failedOrder == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "失败订单不存在");
        }
        if (failedOrder.getStatus() != 0) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "该订单不在待重试状态");
        }

        // 重新发送 MQ 消息
        Map<String, Object> payload = Map.of(
                "transactionId", failedOrder.getTransactionId(),
                "flashSaleId", failedOrder.getFlashSaleId(),
                "productId", failedOrder.getProductId(),
                "userId", failedOrder.getUserId());
        mqProducer.send(MqConstants.TOPIC_FLASH_ORDER, MqConstants.TAG_FLASH_ORDER,
                failedOrder.getTransactionId(), payload);

        failedOrder.setStatus(1);
        failedOrder.setRetryCount(failedOrder.getRetryCount() + 1);
        failedOrderMapper.updateById(failedOrder);

        saveAuditLog(failedOrder.getFlashSaleId(), operatorId, "RETRY",
                "重试失败订单 " + failedOrderId);
        log.info("秒杀失败订单重试: failedOrderId={}, 操作人={}", failedOrderId, operatorId);
    }

    public void cancelFailedOrder(Long failedOrderId, Long operatorId) {
        FlashSaleFailedOrder failedOrder = failedOrderMapper.selectById(failedOrderId);
        if (failedOrder == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "失败订单不存在");
        }

        // 回补库存
        String stockKey = FLASH_STOCK_PREFIX + failedOrder.getProductId() + "}:stock";
        Long currentStock = stringRedisTemplate.opsForValue().increment(stockKey);
        if (currentStock != null) {
            cacheManager.syncLocalStock(failedOrder.getProductId(), currentStock.intValue());
            cacheManager.broadcastStockChange(failedOrder.getProductId(), currentStock.intValue());
        }

        failedOrder.setStatus(3);
        failedOrderMapper.updateById(failedOrder);

        saveAuditLog(failedOrder.getFlashSaleId(), operatorId, "CANCEL_FAILED",
                "取消失败订单 " + failedOrderId + "，已回补库存");
        log.info("秒杀失败订单取消并回补: failedOrderId={}, 操作人={}", failedOrderId, operatorId);
    }

    public void updateRateConfig(Long frequentThreshold, Long maliciousThreshold, Long blacklistMinutes, Long operatorId) {
        rateLimiter.updateConfig(frequentThreshold, maliciousThreshold, blacklistMinutes);
        saveAuditLog(null, operatorId, "RATE_CONFIG",
                "更新限流配置: frequent=" + frequentThreshold + ", malicious=" + maliciousThreshold + ", blacklistMinutes=" + blacklistMinutes);
    }

    public Map<String, Object> getRateConfig() {
        return rateLimiter.getConfig();
    }

    public void compensateFailedOrder(Long failedOrderId, BigDecimal amount, String remark, Long operatorId) {
        FlashSaleFailedOrder failed = failedOrderMapper.selectById(failedOrderId);
        if (failed == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "失败订单不存在");
        }
        if (failed.getStatus() != 0) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "该订单已处理（status=" + failed.getStatus() + "）");
        }

        FlashSaleCompensation compensation = new FlashSaleCompensation();
        compensation.setFailedOrderId(failedOrderId);
        compensation.setUserId(failed.getUserId());
        compensation.setFlashSaleId(failed.getFlashSaleId());
        compensation.setCompensationType("COUPON");
        compensation.setAmount(amount != null ? amount : BigDecimal.ZERO);
        compensation.setStatus(1);
        compensation.setOperatorId(operatorId);
        compensation.setRemark(remark);
        compensation.setCreateTime(LocalDateTime.now());
        compensationMapper.insert(compensation);

        // 标记失败订单为已补偿
        failed.setStatus(1);
        failedOrderMapper.updateById(failed);

        saveAuditLog(failed.getFlashSaleId(), operatorId, "COMPENSATE",
                "补偿失败订单 " + failedOrderId + "，金额=" + compensation.getAmount() + "，备注=" + remark);
        log.info("秒杀失败订单补偿: failedOrderId={}, amount={}, 操作人={}", failedOrderId, amount, operatorId);
    }

    public void manualProcessFailedOrder(Long failedOrderId, String remark, Long operatorId) {
        FlashSaleFailedOrder failed = failedOrderMapper.selectById(failedOrderId);
        if (failed == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "失败订单不存在");
        }
        if (failed.getStatus() != 0 && failed.getStatus() != 1) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "该订单已处理（status=" + failed.getStatus() + "）");
        }

        FlashSaleCompensation compensation = new FlashSaleCompensation();
        compensation.setFailedOrderId(failedOrderId);
        compensation.setUserId(failed.getUserId());
        compensation.setFlashSaleId(failed.getFlashSaleId());
        compensation.setCompensationType("MANUAL");
        compensation.setAmount(BigDecimal.ZERO);
        compensation.setStatus(1);
        compensation.setOperatorId(operatorId);
        compensation.setRemark(remark);
        compensation.setCreateTime(LocalDateTime.now());
        compensationMapper.insert(compensation);

        failed.setStatus(2);
        failedOrderMapper.updateById(failed);

        saveAuditLog(failed.getFlashSaleId(), operatorId, "MANUAL_PROCESS",
                "人工处理失败订单 " + failedOrderId + "，备注=" + remark);
        log.info("秒杀失败订单人工处理: failedOrderId={}, 操作人={}", failedOrderId, operatorId);
    }

    public void addWhitelist(Long flashSaleId, List<Long> userIds, Long operatorId) {
        FlashSale flashSale = flashSaleMapper.selectById(flashSaleId);
        if (flashSale == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "活动不存在");
        }

        for (Long uid : userIds) {
            long exists = flashSaleWhitelistMapper.selectCount(
                    new LambdaQueryWrapper<FlashSaleWhitelist>()
                            .eq(FlashSaleWhitelist::getFlashSaleId, flashSaleId)
                            .eq(FlashSaleWhitelist::getUserId, uid));
            if (exists == 0) {
                FlashSaleWhitelist wl = new FlashSaleWhitelist();
                wl.setFlashSaleId(flashSaleId);
                wl.setUserId(uid);
                wl.setCreateTime(LocalDateTime.now());
                flashSaleWhitelistMapper.insert(wl);
            }
        }

        // 同步白名单到 Redis Set（限流器从 Redis 读取，不再查 DB）
        rateLimiter.syncWhitelistToRedis(flashSaleId, userIds);

        saveAuditLog(flashSaleId, operatorId, "WHITELIST", "添加 " + userIds.size() + " 个白名单用户");
        log.info("秒杀白名单添加完成: flashSaleId={}, 用户数={}", flashSaleId, userIds.size());
    }

    private FlashSaleProductVO buildProductVO(FlashSaleProduct fp, Product product) {
        FlashSaleProductVO vo = new FlashSaleProductVO();
        vo.setId(fp.getId());
        vo.setProductId(fp.getProductId());
        vo.setFlashPrice(fp.getFlashPrice());
        vo.setTotalStock(fp.getTotalStock());
        vo.setMaxPerUser(fp.getMaxPerUser());

        if (product != null) {
            vo.setProductName(product.getName());
            vo.setProductImage(product.getImageUrl());
            vo.setOriginalPrice(product.getPrice());
        }

        String stockKey = FLASH_STOCK_PREFIX + fp.getProductId() + "}:stock";
        String stockStr = stringRedisTemplate.opsForValue().get(stockKey);
        int remaining = stockStr != null ? Integer.parseInt(stockStr) : fp.getTotalStock();
        vo.setRemainingStock(remaining);
        return vo;
    }

    private FlashSaleVO toFlashSaleVO(FlashSale sale) {
        FlashSaleVO vo = new FlashSaleVO();
        vo.setId(sale.getId());
        vo.setTitle(sale.getTitle());
        vo.setStartTime(sale.getStartTime());
        vo.setEndTime(sale.getEndTime());
        vo.setStatus(sale.getStatus());
        return vo;
    }

    private String getOrderStatusMessage(Integer status) {
        if (status == null) {
            return "未知状态";
        }
        return switch (status) {
            case 0 -> "待支付";
            case 1 -> "已支付";
            case 2 -> "已发货";
            case 3 -> "已完成";
            case 4 -> "已取消";
            default -> "未知状态";
        };
    }

    private FlashSaleBuyResult fail(String message) {
        return new FlashSaleBuyResult("FAIL", null, message);
    }

    private void saveAuditLog(Long flashSaleId, Long operatorId, String action, String detail) {
        FlashSaleAuditLog auditLog = new FlashSaleAuditLog();
        auditLog.setFlashSaleId(flashSaleId);
        auditLog.setOperatorId(operatorId);
        auditLog.setAction(action);
        auditLog.setDetail(detail);
        auditLog.setCreateTime(LocalDateTime.now());
        flashSaleAuditLogMapper.insert(auditLog);
    }
}
