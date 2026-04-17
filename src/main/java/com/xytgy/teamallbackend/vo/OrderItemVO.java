package com.xytgy.teamallbackend.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class OrderItemVO {
    private String productName;
    private BigDecimal productPrice;
    private Integer quantity;
}
