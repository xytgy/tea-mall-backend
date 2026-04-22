package com.xytgy.teamallbackend.module.user.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @JsonAlias({"username", "useraccount"})
    @NotBlank(message = "账号不能为空")
    private String userAccount;
    @NotBlank(message = "密码不能为空")
    private String password;
}
