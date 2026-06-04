package com.xytgy.teamallbackend.module.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 
 * @TableName orders
 */
@TableName(value ="orders")
@Data
public class Orders {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 总金额
     */
    private BigDecimal totalAmount;

    /**
     * 状态 0待支付 1已支付 2已发货 3已完成 4已取消
     */
    private Integer status;

    /**
     * 收货人
     */
    private String receiverName;

    /**
     * 手机号
     */
    private String receiverPhone;

    /**
     * 地址
     */
    private String receiverAddress;

    /**
     * 下单时间
     */
    private LocalDateTime createTime;

    /**
     * 支付时间
     */
    private LocalDateTime payTime;
    
    /**
     * 拒绝退款原因
     */
    private String refusalReason;

    /**
     * 关联最终成功的支付流水 ID
     */
    private Long paymentId;

    /**
     * 抽佣比例快照
     */
    private BigDecimal commissionRate;

    /**
     * 抽佣基数
     */
    private BigDecimal feeBaseAmount;

    /**
     * 平台抽佣金额
     */
    private BigDecimal platformFee;

    /**
     * 商家应结金额
     */
    private BigDecimal merchantAmount;

    /**
     * 结算状态：0-未结算, 1-结算中, 2-已结算
     */
    private Integer settleStatus;

    /**
     * 预计结算时间
     */
    private LocalDateTime settleTime;

    /**
     * 订单来源：0普通 1秒杀
     */
    private Integer source;

    /**
     * 逻辑删除 0否 1是
     */
    @TableLogic
    private Integer isDeleted;
}