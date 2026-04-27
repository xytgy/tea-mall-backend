package com.xytgy.teamallbackend.module.order.dto;

import lombok.Data;
import java.util.List;

@Data
public class OrderReviewRequest {
    private Long orderId;
    private Long productId;
    private Integer rating;
    private String content;
    private List<String> images;
}