package com.xytgy.teamallbackend.module.cart.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CartDeleteRequest {
    @NotEmpty(message = "删除列表不能为空")
    private List<Long> ids;
}
