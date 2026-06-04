package com.xytgy.teamallbackend.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddressAddRequest {
    @NotBlank(message = "收货人姓名不能为空")
    private String receiverName;
    @NotBlank(message = "收货人手机号不能为空")
    private String receiverPhone;
    @NotBlank(message = "收货地址不能为空")
    private String receiverAddress;
    private Boolean isDefault;
}
