package com.xytgy.teamallbackend.module.order.dto;

import lombok.Data;

import java.util.List;

@Data
public class OrderCreateRequest {
    private List<Item> items;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;

    @Data
    public static class Item {
        private Long productId;
        private Integer quantity;
    }
}
