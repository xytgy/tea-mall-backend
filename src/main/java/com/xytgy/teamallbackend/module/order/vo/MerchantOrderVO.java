package com.xytgy.teamallbackend.module.order.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class MerchantOrderVO {
    private Long id;
    private String orderNo;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private BigDecimal totalAmount;
    private String createTime;
    private Integer status;
    private String refusalReason;
    private List<OrderItemVO> items;
}
