package com.xytgy.teamallbackend.module.cart.dto;

import lombok.Data;

@Data
public class CartUpdateRequest {
    private Long id;
    private Integer quantity;
}
