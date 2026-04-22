package com.xytgy.teamallbackend.module.order.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreateOrderVO {
    private String orderNo;
    private Long orderId;
}
