package com.xytgy.teamallbackend.module.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProductAuditRequest {
    @NotNull(message = "商品ID不能为空")
    private Long id;
    @NotNull(message = "审核状态不能为空")
    @Min(value = 1, message = "审核状态值非法")
    @Max(value = 2, message = "审核状态值非法")
    private Integer status; // 1: 审核通过, 2: 驳回
}
