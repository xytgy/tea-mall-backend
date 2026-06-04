package com.xytgy.teamallbackend.module.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class OrderCreateRequest {
    @NotEmpty(message = "订单商品不能为空")
    @Valid
    private List<Item> items;
    @NotBlank(message = "收货人姓名不能为空")
    private String receiverName;
    @NotBlank(message = "收货人手机号不能为空")
    private String receiverPhone;
    @NotBlank(message = "收货地址不能为空")
    private String receiverAddress;

    @Data
    public static class Item {
        @NotNull(message = "商品ID不能为空")
        private Long productId;
        @NotNull(message = "数量不能为空")
        @Min(value = 1, message = "数量不能小于1")
        private Integer quantity;
    }
}
