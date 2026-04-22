package com.xytgy.teamallbackend.module.cart.dto;

import lombok.Data;

import java.util.List;

@Data
public class CartDeleteRequest {
    private List<Long> ids;
}