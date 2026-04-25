package com.xytgy.teamallbackend.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "账号不能为空")
    @Size(min = 8, max = 32, message = "账号长度需在8-32之间")
    private String userAccount;
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 32, message = "密码长度需在8-32之间")
    private String password;
    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确")
    private String phone;
}
