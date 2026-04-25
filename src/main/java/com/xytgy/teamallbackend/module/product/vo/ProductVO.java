package com.xytgy.teamallbackend.module.product.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ProductVO {
    private Long id;
    private String name;
    private String category;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private String imageUrl;
    private Integer status;
    private Integer sales;
    private Long merchantId;
}
