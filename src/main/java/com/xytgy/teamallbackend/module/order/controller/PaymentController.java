package com.xytgy.teamallbackend.module.order.controller;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.domain.AlipayTradeCloseModel;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.alipay.api.internal.util.AlipaySignature;
import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.mq.message.order.PaymentSuccessMessage;
import com.xytgy.teamallbackend.mq.publisher.PaymentEventPublisher;
import com.xytgy.teamallbackend.security.SecurityUtils;
import com.xytgy.teamallbackend.config.AlipayConfig;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.order.entity.Orders;
import com.xytgy.teamallbackend.module.order.entity.PaymentRecord;
import com.xytgy.teamallbackend.module.order.service.OrdersService;
import com.xytgy.teamallbackend.module.order.service.PaymentRecordService;
import com.xytgy.teamallbackend.lock.DistributedLock;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/payment")
@Tag(name = "支付宝支付接口")
@ConditionalOnProperty(prefix = "alipay", name = "enabled", havingValue = "true")
public class PaymentController {

    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILURE = "failure";

    private final AlipayClient alipayClient;

    private final AlipayConfig alipayConfig;

    private final OrdersService ordersService;

    private final PaymentRecordService paymentRecordService;
    private final DistributedLock distributedLock;
    private final PaymentEventPublisher paymentEventPublisher;
    private final StringRedisTemplate stringRedisTemplate;

    /** 支付回调幂等性 key 前缀 */
    private static final String IDEMPOTENT_PREFIX = "payment:idempotent:";
    /** 幂等性 key 过期时间（小时） */
    private static final long IDEMPOTENT_EXPIRE_HOURS = 24;

    public PaymentController(AlipayClient alipayClient, AlipayConfig alipayConfig, OrdersService ordersService, PaymentRecordService paymentRecordService, DistributedLock distributedLock, PaymentEventPublisher paymentEventPublisher, StringRedisTemplate stringRedisTemplate) {
        this.alipayClient = alipayClient;
        this.alipayConfig = alipayConfig;
        this.ordersService = ordersService;
        this.paymentRecordService = paymentRecordService;
        this.distributedLock = distributedLock;
        this.paymentEventPublisher = paymentEventPublisher;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @RequestMapping(value = "/alipay/pay", method = {RequestMethod.GET, RequestMethod.POST}, produces = "text/html;charset=UTF-8")
    @Operation(summary = "发起电脑网站支付")
    public String pay(@RequestParam Long orderId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Orders order = ordersService.getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new ServiceException(ResultCode.NOT_FOUND, "订单不存在或无权操作");
        }
        if (order.getStatus() != Orders.STATUS_PENDING_PAYMENT) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "订单状态不是待支付");
        }

        // 创建支付流水
        String outTradeNo = "pay_" + order.getOrderNo() + "_" + System.currentTimeMillis();
        PaymentRecord paymentRecord = new PaymentRecord();
        paymentRecord.setOrderId(orderId);
        paymentRecord.setOutTradeNo(outTradeNo);
        paymentRecord.setPayChannel("ALIPAY_PC");
        paymentRecord.setTotalAmount(order.getTotalAmount());
        paymentRecord.setStatus(PaymentRecord.STATUS_PAYING);
        paymentRecord.setCreateTime(LocalDateTime.now());
        paymentRecordService.save(paymentRecord);

        // 调用支付宝接口
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(alipayConfig.getNotifyUrl());
        request.setReturnUrl(alipayConfig.getReturnUrl());

        AlipayTradePagePayModel model = new AlipayTradePagePayModel();
        model.setOutTradeNo(outTradeNo);
        model.setTotalAmount(order.getTotalAmount().toString());
        model.setSubject("云栖茗茶订单-" + order.getOrderNo());
        model.setProductCode("FAST_INSTANT_TRADE_PAY");
        request.setBizModel(model);

        try {
            AlipayTradePagePayResponse response = alipayClient.pageExecute(request, "POST");
            if (response.isSuccess()) {
                return response.getBody();
            } else {
                throw new ServiceException(ResultCode.ERROR, "发起支付失败: " + response.getSubMsg());
            }
        } catch (AlipayApiException e) {
            throw new ServiceException(ResultCode.ERROR, "支付宝接口调用异常: " + e.getMessage());
        }
    }

    // S3776: 提取共享补单逻辑，降低 notifyCallback 和 queryPayResult 的复杂度
    private void confirmPayment(PaymentRecord paymentRecord, String tradeNo) {
        paymentRecord.setStatus(PaymentRecord.STATUS_PAID);
        paymentRecord.setTradeNo(tradeNo);
        paymentRecord.setPayTime(LocalDateTime.now());
        paymentRecordService.updateById(paymentRecord);

        Orders order = ordersService.getById(paymentRecord.getOrderId());
        if (order != null && order.getStatus() == Orders.STATUS_PENDING_PAYMENT) {
            order.setStatus(Orders.STATUS_PAID);
            order.setPaymentId(paymentRecord.getId());
            order.setPayTime(LocalDateTime.now());
            ordersService.updateById(order);

            boolean published = paymentEventPublisher.publishPaymentSuccess(
                    PaymentSuccessMessage.builder()
                            .orderId(order.getId())
                            .userId(order.getUserId())
                            .paymentId(paymentRecord.getId())
                            .build()
            );
            if (!published) {
                log.warn("RocketMQ 不可用，消息未发送");
            }
        }
    }

    private boolean verifyNotifyParams(Map<String, String> params) throws AlipayApiException {
        if (!AlipaySignature.rsaCheckV1(params, alipayConfig.getAlipayPublicKey(),
                alipayConfig.getCharset(), alipayConfig.getSignType())) {
            return false;
        }
        return alipayConfig.getAppId().equals(params.get("app_id"));
    }

    @PostMapping("/alipay/notify")
    @Operation(summary = "支付宝异步通知")
    @Transactional(rollbackFor = Exception.class)
    public String notifyCallback(@RequestParam Map<String, String> params) {
        String outTradeNo = null;
        String idempotentKey = null;
        try {
            if (!verifyNotifyParams(params)) {
                return STATUS_FAILURE;
            }

            outTradeNo = params.get("out_trade_no");
            String tradeNo = params.get("trade_no");
            String tradeStatus = params.get("trade_status");

            // Redis 幂等性保护：使用 SETNX 防止同一笔交易的回调被重复处理
            idempotentKey = IDEMPOTENT_PREFIX + outTradeNo;
            Boolean isNew = stringRedisTemplate.opsForValue()
                    .setIfAbsent(idempotentKey, "1", IDEMPOTENT_EXPIRE_HOURS, TimeUnit.HOURS);
            if (Boolean.FALSE.equals(isNew)) {
                // 已经处理过该回调，直接返回成功（避免支付宝重复通知）
                log.info("支付回调幂等拦截, outTradeNo={}", outTradeNo);
                return STATUS_SUCCESS;
            }

            PaymentRecord paymentRecord = paymentRecordService.getByOutTradeNo(outTradeNo);
            if (paymentRecord == null) {
                // 数据异常，删除幂等 key 允许重试
                stringRedisTemplate.delete(idempotentKey);
                return STATUS_FAILURE;
            }
            if (new BigDecimal(params.get("total_amount")).compareTo(paymentRecord.getTotalAmount()) != 0) {
                // 金额不匹配，删除幂等 key 允许重试
                stringRedisTemplate.delete(idempotentKey);
                return STATUS_FAILURE;
            }

            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                if (PaymentRecord.STATUS_PAID.equals(paymentRecord.getStatus())) {
                    return STATUS_SUCCESS;
                }
                String lockKey = "payment:notify:" + outTradeNo;
                if (!distributedLock.tryLock(lockKey)) {
                    return STATUS_SUCCESS;
                }
                try {
                    paymentRecord = paymentRecordService.getByOutTradeNo(outTradeNo);
                    if (PaymentRecord.STATUS_PAID.equals(paymentRecord.getStatus())) {
                        return STATUS_SUCCESS;
                    }
                    confirmPayment(paymentRecord, tradeNo);
                } finally {
                    distributedLock.unlock(lockKey);
                }
            }
            return STATUS_SUCCESS;
        } catch (AlipayApiException e) {
            log.error("支付宝回调处理异常", e);
            // 处理失败，删除幂等 key 允许支付宝重试
            if (idempotentKey != null) {
                stringRedisTemplate.delete(idempotentKey);
            }
            return STATUS_FAILURE;
        } catch (Exception e) {
            log.error("支付回调处理异常, outTradeNo={}", outTradeNo, e);
            // 处理失败，删除幂等 key 允许重试
            if (idempotentKey != null) {
                stringRedisTemplate.delete(idempotentKey);
            }
            return STATUS_FAILURE;
        }
    }

    // S3776: 使用共享 confirmPayment 方法，降低方法复杂度
    @GetMapping("/alipay/query")
    @Operation(summary = "主动查询支付结果")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> queryPayResult(@RequestParam Long orderId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Orders order = ordersService.getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new ServiceException(ResultCode.NOT_FOUND, "订单不存在");
        }
        if (order.getStatus() > Orders.STATUS_PENDING_PAYMENT) {
            return Result.success("支付状态", PaymentRecord.STATUS_PAID);
        }

        PaymentRecord paymentRecord = paymentRecordService.getLastPayingRecord(orderId);
        if (paymentRecord == null) {
            return Result.success("支付状态", "NOT_PAYING");
        }

        try {
            AlipayTradeQueryResponse response = queryTradeStatus(paymentRecord.getOutTradeNo());
            return Result.success("支付状态", resolveTradeStatus(response, paymentRecord));
        } catch (AlipayApiException e) {
            log.error("查询支付宝支付状态异常", e);
        }
        return Result.success("支付状态", "UNKNOWN");
    }

    private AlipayTradeQueryResponse queryTradeStatus(String outTradeNo) throws AlipayApiException {
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        AlipayTradeQueryModel model = new AlipayTradeQueryModel();
        model.setOutTradeNo(outTradeNo);
        request.setBizModel(model);
        return alipayClient.execute(request);
    }

    private String resolveTradeStatus(AlipayTradeQueryResponse response, PaymentRecord paymentRecord) {
        if (!response.isSuccess()) {
            return "UNKNOWN";
        }
        String status = response.getTradeStatus();
        if ("TRADE_SUCCESS".equals(status) || "TRADE_FINISHED".equals(status)) {
            String lockKey = "payment:notify:" + paymentRecord.getOutTradeNo();
            if (!distributedLock.tryLock(lockKey)) {
                return PaymentRecord.STATUS_PAID;
            }
            try {
                paymentRecord = paymentRecordService.getByOutTradeNo(paymentRecord.getOutTradeNo());
                if (PaymentRecord.STATUS_PAID.equals(paymentRecord.getStatus())) {
                    return PaymentRecord.STATUS_PAID;
                }
                confirmPayment(paymentRecord, response.getTradeNo());
            } finally {
                distributedLock.unlock(lockKey);
            }
            return PaymentRecord.STATUS_PAID;
        }
        if ("WAIT_BUYER_PAY".equals(status)) {
            return PaymentRecord.STATUS_PAYING;
        }
        return PaymentRecord.STATUS_CLOSED;
    }

    @PostMapping("/alipay/close")
    @Operation(summary = "关闭订单")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> closeOrder(@RequestParam Long orderId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Orders order = ordersService.getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new ServiceException(ResultCode.NOT_FOUND, "订单不存在");
        }
        if (order.getStatus() != Orders.STATUS_PENDING_PAYMENT) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "只能关闭待支付订单");
        }

        PaymentRecord paymentRecord = paymentRecordService.getLastPayingRecord(orderId);
        if (paymentRecord != null) {
            AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
            AlipayTradeCloseModel model = new AlipayTradeCloseModel();
            model.setOutTradeNo(paymentRecord.getOutTradeNo());
            request.setBizModel(model);
            try {
                AlipayTradeCloseResponse response = alipayClient.execute(request);
                if (response.isSuccess()) {
                    paymentRecord.setStatus(PaymentRecord.STATUS_CLOSED);
                    paymentRecordService.updateById(paymentRecord);
                }
            } catch (AlipayApiException e) {
                log.error("支付宝关单异常, outTradeNo={}", paymentRecord.getOutTradeNo(), e);
            }
        }

        order.setStatus(Orders.STATUS_CANCELLED);
        ordersService.updateById(order);
        return Result.success(null);
    }

    @PostMapping("/alipay/refund/{orderId}")
    @Operation(summary = "订单退款")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> refundOrder(@PathVariable Long orderId, @RequestParam(required = false) BigDecimal amount) {
        Long userId = SecurityUtils.getCurrentUserId();
        Orders order = ordersService.getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new ServiceException(ResultCode.NOT_FOUND, "订单不存在或无权操作");
        }

        if (order.getStatus() != Orders.STATUS_PAID && order.getStatus() != Orders.STATUS_SHIPPED) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "订单状态不支持退款");
        }

        return distributedLock.executeWithLock("payment:refund:" + orderId, () -> {
            PaymentRecord paymentRecord = null;
            if (order.getPaymentId() != null) {
                paymentRecord = paymentRecordService.getById(order.getPaymentId());
            }

            if (paymentRecord == null || !PaymentRecord.STATUS_PAID.equals(paymentRecord.getStatus())) {
                throw new ServiceException(ResultCode.BAD_REQUEST, "无有效支付记录");
            }

            BigDecimal refundAmount = amount != null ? amount : order.getTotalAmount();
            if (refundAmount.compareTo(order.getTotalAmount()) > 0) {
                throw new ServiceException(ResultCode.BAD_REQUEST, "退款金额不能超过订单金额");
            }
            if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ServiceException(ResultCode.BAD_REQUEST, "退款金额必须大于零");
            }
            PaymentRecord existingRefund = paymentRecordService.lambdaQuery()
                    .eq(PaymentRecord::getOrderId, orderId)
                    .eq(PaymentRecord::getStatus, PaymentRecord.STATUS_REFUNDED)
                    .one();
            if (existingRefund != null) {
                throw new ServiceException(ResultCode.CONFLICT, "该订单已退款，请勿重复操作");
            }
            String outRequestNo = "refund_" + order.getOrderNo() + "_" + System.currentTimeMillis();

            AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
            AlipayTradeRefundModel model = new AlipayTradeRefundModel();
            model.setOutTradeNo(paymentRecord.getOutTradeNo());
            model.setRefundAmount(refundAmount.toString());
            model.setOutRequestNo(outRequestNo);
            request.setBizModel(model);

            try {
                AlipayTradeRefundResponse response = alipayClient.execute(request);
                if (response.isSuccess()) {
                    order.setStatus(Orders.STATUS_REFUNDED);
                    ordersService.updateById(order);
                    paymentRecord.setStatus(PaymentRecord.STATUS_REFUNDED);
                    paymentRecordService.updateById(paymentRecord);
                    return Result.success(null);
                } else {
                    throw new ServiceException(ResultCode.ERROR, "退款失败: " + response.getSubMsg());
                }
            } catch (AlipayApiException e) {
                throw new ServiceException(ResultCode.ERROR, "支付宝接口异常: " + e.getMessage());
            }
        });
    }
}
