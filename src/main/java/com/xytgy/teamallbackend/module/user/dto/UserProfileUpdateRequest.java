package com.xytgy.teamallbackend.module.user.dto;

import lombok.Data;

@Data
public class UserProfileUpdateRequest {
    private String nickname;
    private String bio;
    private Integer gender;
    private String phone;
}
