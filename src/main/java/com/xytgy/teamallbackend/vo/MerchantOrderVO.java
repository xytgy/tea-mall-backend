package com.xytgy.teamallbackend.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class MerchantOrderVO {
    private Long id;
    private String orderNo;
    private String receiverName;
    private BigDecimal totalAmount;
    private String createTime;
    private Integer status;
}
