package com.xytgy.teamallbackend.module.shop.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ShopUpdateRequest {
    
    @NotBlank(message = "店铺名称不能为空")
    private String shopName;
    
    private String businessLicense;
}
