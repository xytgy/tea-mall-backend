package com.xytgy.teamallbackend.module.shop.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ShopVO {
    private Long id;
    private Long userId;
    private String name;
    private String logo;
    private String description;
    private Double rating;
    private Integer monthlySales;
    private Integer fans;
    private Boolean isFollowed;
    private String phone;
    private String address;
    private String businessHours;
    private String businessLicense;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
