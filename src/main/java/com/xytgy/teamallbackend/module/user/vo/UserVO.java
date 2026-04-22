package com.xytgy.teamallbackend.module.user.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserVO {
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private Integer gender;
    private String phone;
    private String email;
    private Integer status;
    private Integer role;
    private String createTime;
}