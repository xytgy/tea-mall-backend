package com.xytgy.teamallbackend.module.favorite.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FavoriteRemoveRequest {
    @NotNull(message = "商品ID不能为空")
    private Long productId;
}