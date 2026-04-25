package com.xytgy.teamallbackend.module.user.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserVO {
    private Long id;
    private String userAccount;
    private String nickname;
    private String avatar;
    private Integer gender;
    private String phone;
    private String email;
    private Integer status;
    private Integer role; // 返回前端映射后的角色：0普通买家 1商家 2管理员
    private String createTime;
}