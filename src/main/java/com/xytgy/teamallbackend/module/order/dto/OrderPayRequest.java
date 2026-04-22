package com.xytgy.teamallbackend.module.order.dto;

import lombok.Data;

@Data
public class OrderPayRequest {
    private Long orderId;
    private String payMethod;
}