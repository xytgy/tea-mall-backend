package com.xytgy.teamallbackend.module.order.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderStatsVO {
    private Integer unpaid;
    private Integer packing;
    private Integer delivering;
    private Integer reviewing;
}
