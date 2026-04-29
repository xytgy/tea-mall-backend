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
import com.xytgy.teamallbackend.common.UserContext;
import com.xytgy.teamallbackend.config.AlipayConfig;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.order.entity.Orders;
import com.xytgy.teamallbackend.module.order.entity.PaymentRecord;
import com.xytgy.teamallbackend.module.order.service.OrdersService;
import com.xytgy.teamallbackend.module.order.service.PaymentRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payment")
@Tag(name = "支付宝支付接口")
public class PaymentController {

    @Autowired
    private AlipayClient alipayClient;

    @Autowired
    private AlipayConfig alipayConfig;

    @Autowired
    private OrdersService ordersService;

    @Autowired
    private PaymentRecordService paymentRecordService;

    @PostMapping("/alipay/pay")
    @Operation(summary = "发起电脑网站支付")
    public Result<String> pay(@RequestParam Long orderId) {
        Long userId = UserContext.getCurrentUserId();
        Orders order = ordersService.getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new ServiceException(ResultCode.NOT_FOUND, "订单不存在或无权操作");
        }
        if (order.getStatus() != 0) { // 0:待支付
            throw new ServiceException(ResultCode.BAD_REQUEST, "订单状态不是待支付");
        }

        // 创建支付流水
        String outTradeNo = "pay_" + order.getOrderNo() + "_" + System.currentTimeMillis();
        PaymentRecord record = new PaymentRecord();
        record.setOrderId(orderId);
        record.setOutTradeNo(outTradeNo);
        record.setPayChannel("ALIPAY_PC");
        record.setTotalAmount(order.getTotalAmount());
        record.setStatus("PAYING");
        record.setCreateTime(LocalDateTime.now());
        paymentRecordService.save(record);

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
                return Result.success("获取支付表单成功", response.getBody());
            } else {
                throw new ServiceException(ResultCode.ERROR, "发起支付失败: " + response.getSubMsg());
            }
        } catch (AlipayApiException e) {
            throw new ServiceException(ResultCode.ERROR, "支付宝接口调用异常: " + e.getMessage());
        }
    }

    @PostMapping("/alipay/notify")
    @Operation(summary = "支付宝异步通知")
    @Transactional(rollbackFor = Exception.class)
    public String notifyCallback(@RequestParam Map<String, String> params) {
        try {
            boolean signVerified = AlipaySignature.rsaCheckV1(params, alipayConfig.getAlipayPublicKey(),
                    alipayConfig.getCharset(), alipayConfig.getSignType());

            if (!signVerified) {
                return "failure";
            }

            String outTradeNo = params.get("out_trade_no");
            String tradeNo = params.get("trade_no");
            String tradeStatus = params.get("trade_status");
            String totalAmountStr = params.get("total_amount");
            String appId = params.get("app_id");

            if (!alipayConfig.getAppId().equals(appId)) {
                return "failure";
            }

            PaymentRecord record = paymentRecordService.getByOutTradeNo(outTradeNo);
            if (record == null) {
                return "failure";
            }

            if (new BigDecimal(totalAmountStr).compareTo(record.getTotalAmount()) != 0) {
                return "failure";
            }

            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                if ("PAID".equals(record.getStatus())) {
                    return "success";
                }
                
                // 更新流水状态
                record.setStatus("PAID");
                record.setTradeNo(tradeNo);
                record.setPayTime(LocalDateTime.now());
                paymentRecordService.updateById(record);

                // 更新订单状态
                Orders order = ordersService.getById(record.getOrderId());
                if (order != null && order.getStatus() == 0) {
                    order.setStatus(1); // 1:待发货 (已支付)
                    order.setPaymentId(record.getId());
                    order.setPayTime(LocalDateTime.now());
                    ordersService.updateById(order);
                }
            }

            return "success";
        } catch (AlipayApiException e) {
            e.printStackTrace();
            return "failure";
        }
    }

    @GetMapping("/alipay/query")
    @Operation(summary = "主动查询支付结果")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> queryPayResult(@RequestParam Long orderId) {
        Long userId = UserContext.getCurrentUserId();
        Orders order = ordersService.getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new ServiceException(ResultCode.NOT_FOUND, "订单不存在");
        }

        if (order.getStatus() > 0) {
            return Result.success("支付状态", "PAID");
        }

        PaymentRecord record = paymentRecordService.getLastPayingRecord(orderId);
        if (record == null) {
            return Result.success("支付状态", "NOT_PAYING");
        }

        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        AlipayTradeQueryModel model = new AlipayTradeQueryModel();
        model.setOutTradeNo(record.getOutTradeNo());
        request.setBizModel(model);

        try {
            AlipayTradeQueryResponse response = alipayClient.execute(request);
            if (response.isSuccess()) {
                if ("TRADE_SUCCESS".equals(response.getTradeStatus()) || "TRADE_FINISHED".equals(response.getTradeStatus())) {
                    // 补单逻辑
                    record.setStatus("PAID");
                    record.setTradeNo(response.getTradeNo());
                    record.setPayTime(LocalDateTime.now());
                    paymentRecordService.updateById(record);

                    order.setStatus(1);
                    order.setPaymentId(record.getId());
                    order.setPayTime(LocalDateTime.now());
                    ordersService.updateById(order);

                    return Result.success("支付状态", "PAID");
                } else if ("WAIT_BUYER_PAY".equals(response.getTradeStatus())) {
                    return Result.success("支付状态", "PAYING");
                } else {
                    return Result.success("支付状态", "CLOSED");
                }
            }
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        return Result.success("支付状态", "UNKNOWN");
    }

    @PostMapping("/alipay/close")
    @Operation(summary = "关闭订单")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> closeOrder(@RequestParam Long orderId) {
        Long userId = UserContext.getCurrentUserId();
        Orders order = ordersService.getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new ServiceException(ResultCode.NOT_FOUND, "订单不存在");
        }
        if (order.getStatus() != 0) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "只能关闭待支付订单");
        }

        PaymentRecord record = paymentRecordService.getLastPayingRecord(orderId);
        if (record != null) {
            AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
            AlipayTradeCloseModel model = new AlipayTradeCloseModel();
            model.setOutTradeNo(record.getOutTradeNo());
            request.setBizModel(model);
            try {
                AlipayTradeCloseResponse response = alipayClient.execute(request);
                if (response.isSuccess()) {
                    record.setStatus("CLOSED");
                    paymentRecordService.updateById(record);
                }
            } catch (AlipayApiException e) {
                e.printStackTrace();
            }
        }

        order.setStatus(4); // 4:已取消
        ordersService.updateById(order);
        return Result.success(null);
    }

    @PostMapping("/alipay/refund/{orderId}")
    @Operation(summary = "订单退款")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> refundOrder(@PathVariable Long orderId, @RequestParam(required = false) BigDecimal amount) {
        Long userId = UserContext.getCurrentUserId();
        Orders order = ordersService.getById(orderId);
        if (order == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "订单不存在");
        }
        
        if (order.getStatus() != 1 && order.getStatus() != 2) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "订单状态不支持退款");
        }

        PaymentRecord record = null;
        if (order.getPaymentId() != null) {
            record = paymentRecordService.getById(order.getPaymentId());
        }
        
        if (record == null || !"PAID".equals(record.getStatus())) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "无有效支付记录");
        }

        BigDecimal refundAmount = amount != null ? amount : order.getTotalAmount();
        String outRequestNo = "refund_" + order.getOrderNo() + "_" + System.currentTimeMillis();

        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        AlipayTradeRefundModel model = new AlipayTradeRefundModel();
        model.setOutTradeNo(record.getOutTradeNo());
        model.setRefundAmount(refundAmount.toString());
        model.setOutRequestNo(outRequestNo);
        request.setBizModel(model);

        try {
            AlipayTradeRefundResponse response = alipayClient.execute(request);
            if (response.isSuccess()) {
                order.setStatus(7); // 假设7为已退款，原系统可能是别的值
                ordersService.updateById(order);
                record.setStatus("REFUNDED");
                paymentRecordService.updateById(record);
                return Result.success(null);
            } else {
                throw new ServiceException(ResultCode.ERROR, "退款失败: " + response.getSubMsg());
            }
        } catch (AlipayApiException e) {
            throw new ServiceException(ResultCode.ERROR, "支付宝接口异常: " + e.getMessage());
        }
    }
}