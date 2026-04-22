package com.xytgy.teamallbackend.module.cart.dto;

import lombok.Data;

@Data
public class CartAddRequest {
    private Long productId;
    private Integer quantity;
}
