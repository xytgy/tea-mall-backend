package com.xytgy.teamallbackend.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CartItemVO {
    private Long id;
    private Long productId;
    private String productName;
    private BigDecimal productPrice;
    private Integer quantity;
    private String imageUrl;
}
