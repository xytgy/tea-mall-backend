package com.xytgy.teamallbackend.dto;

import lombok.Data;

@Data
public class CartAddRequest {
    private Long productId;
    private Integer quantity;
}
