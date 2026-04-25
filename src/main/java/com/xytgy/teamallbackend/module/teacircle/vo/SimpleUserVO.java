package com.xytgy.teamallbackend.module.teacircle.vo;

import lombok.Data;

@Data
public class SimpleUserVO {
    private Long id;
    private String userAccount;
    private String nickname;
    private String avatar;
    private String bio;
    private Boolean isFollowing;
}
