package com.xytgy.teamallbackend.module.teacircle.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserProfileVO {
    private Long id;
    private String nickname;
    private String avatar;
    private String bio;
    private Integer followingCount;
    private Integer followersCount;
    private Integer likeReceivedCount;
    private Boolean isFollowing;
}