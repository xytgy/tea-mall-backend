package com.xytgy.teamallbackend.module.teacircle.vo;

import lombok.Data;
import java.util.List;

@Data
public class TeaPostVO {
    private Long id;
    private Long userId;
    private String userAccount;
    private String nickname;
    private String avatar;
    private String content;
    private List<String> images;
    private Integer likeCount;
    private Integer commentCount;
    private Boolean isLiked;
    private Boolean isFollowing;
    private String createTime;
}
