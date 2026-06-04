package com.xytgy.teamallbackend.module.flashsale.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CaptchaVerifyRequest {
    @NotBlank(message = "验证码UUID不能为空")
    private String uuid;

    @NotBlank(message = "验证码不能为空")
    private String code;
}
