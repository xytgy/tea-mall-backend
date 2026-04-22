package com.xytgy.teamallbackend.module.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductUpdateRequest {
    @NotNull(message = "商品ID不能为空")
    private Long id;
    private String name;
    private String category;
    private String imageUrl;
    private String description;
    @DecimalMin(value = "0.0", inclusive = false, message = "价格必须大于0")
    private BigDecimal price;
    @Min(value = 0, message = "库存不能小于0")
    private Integer stock;
    @Min(value = 0, message = "状态值非法")
    @Max(value = 1, message = "状态值非法")
    private Integer status;
}
