package com.xytgy.teamallbackend.module.product.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductReviewVO {
    private Long id;
    private String username;
    private String avatar;
    private Integer rating;
    private String content;
    private String createTime;
}