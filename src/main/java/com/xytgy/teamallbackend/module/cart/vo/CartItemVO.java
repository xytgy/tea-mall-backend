package com.xytgy.teamallbackend.module.cart.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemVO {
    private Long id;
    private Long productId;

    @JsonProperty("productName")
    private String name;

    @JsonProperty("productPrice")
    private BigDecimal price;

    private Integer quantity;
    private Integer stock;
    private String imageUrl;
}
