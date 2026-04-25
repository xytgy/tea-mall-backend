package com.xytgy.teamallbackend.module.order.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemVO {
    @JsonProperty("productName")
    private String productName;

    @JsonProperty("productPrice")
    private BigDecimal productPrice;

    private Integer quantity;
}
