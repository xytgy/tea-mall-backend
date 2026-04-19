package com.xytgy.teamallbackend.dto;

import lombok.Data;

@Data
public class OrderPayRequest {
    private Long orderId;
    private String payMethod;
}