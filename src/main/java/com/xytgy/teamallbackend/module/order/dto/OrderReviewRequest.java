package com.xytgy.teamallbackend.module.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class OrderReviewRequest {
    @NotNull(message = "订单ID不能为空")
    private Long orderId;
    @NotNull(message = "商品ID不能为空")
    private Long productId;
    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最低为1")
    @Max(value = 5, message = "评分最高为5")
    private Integer rating;
    @NotBlank(message = "评价内容不能为空")
    @Size(min = 5, message = "评价内容不能少于5个字符")
    private String content;
    private List<String> images;
}
