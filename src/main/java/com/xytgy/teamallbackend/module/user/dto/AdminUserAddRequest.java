package com.xytgy.teamallbackend.module.user.dto;

import lombok.Data;

@Data
public class AdminUserAddRequest {
    private String username;
    private Integer role;
    private Integer status;
}
