package com.xytgy.teamallbackend.module.flashsale.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FlashSaleProductVO {
    private Long id;
    private Long productId;
    private String productName;
    private String productImage;
    private BigDecimal originalPrice;
    private BigDecimal flashPrice;
    private Integer totalStock;
    private Integer remainingStock;
    private Integer maxPerUser;
}
