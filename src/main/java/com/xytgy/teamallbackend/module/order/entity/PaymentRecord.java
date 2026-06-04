package com.xytgy.teamallbackend.module.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("payment_record")
public class PaymentRecord {
    // S1192: 支付状态常量，避免重复字符串字面量
    public static final String STATUS_PAYING = "PAYING";
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_CLOSED = "CLOSED";
    public static final String STATUS_REFUNDED = "REFUNDED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private String outTradeNo;
    private String tradeNo;
    private String payChannel;
    private BigDecimal totalAmount;
    private String status; // PAYING, PAID, CLOSED, FAILED, REFUNDED
    private LocalDateTime createTime;
    private LocalDateTime payTime;
}