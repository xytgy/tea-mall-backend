package com.xytgy.teamallbackend.module.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminUserAddRequest {
    @NotBlank(message = "用户名不能为空")
    private String userAccount;
    @NotNull(message = "角色不能为空")
    @Min(value = 0, message = "角色值非法")
    @Max(value = 2, message = "角色值非法")
    private Integer role;
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态值非法")
    @Max(value = 1, message = "状态值非法")
    private Integer status;
}
