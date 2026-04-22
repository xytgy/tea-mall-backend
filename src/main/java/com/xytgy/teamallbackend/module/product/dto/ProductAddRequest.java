package com.xytgy.teamallbackend.module.product.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductAddRequest {
    private String name;
    private String category;
    private String imageUrl;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private Integer status;
}
