package com.xytgy.teamallbackend.module.flashsale.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class FlashSaleVO {
    private Long id;
    private String title;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    private List<FlashSaleProductVO> products;
}
