package com.xytgy.teamallbackend.module.shop.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ShopVO {
    private Long id;
    private Long userId;
    private String shopName;
    private String businessLicense;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
