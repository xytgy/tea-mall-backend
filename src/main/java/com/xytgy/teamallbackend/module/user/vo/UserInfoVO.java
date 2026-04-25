package com.xytgy.teamallbackend.module.user.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserInfoVO {
    private Long id;
    private String userAccount;
    private String nickname;
    private String bio;
    private Integer gender;
    private String phone;
    private String avatar;
    private Integer role;
    private Long shopId;
}
