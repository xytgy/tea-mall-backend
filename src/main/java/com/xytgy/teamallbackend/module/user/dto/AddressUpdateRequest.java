package com.xytgy.teamallbackend.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddressUpdateRequest {
    @NotNull(message = "地址ID不能为空")
    private Long id;
    @NotBlank(message = "收货人姓名不能为空")
    private String receiverName;
    @NotBlank(message = "收货人手机号不能为空")
    private String receiverPhone;
    @NotBlank(message = "收货地址不能为空")
    private String receiverAddress;
    private Boolean isDefault;
}
