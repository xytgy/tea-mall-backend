package com.xytgy.teamallbackend.module.product.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MerchantGoodsAddRequest {
    private String name;
    private BigDecimal price;
    private Integer stock;
    private Integer status;
}
