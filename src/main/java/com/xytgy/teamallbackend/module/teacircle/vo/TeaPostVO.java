package com.xytgy.teamallbackend.module.teacircle.vo;

import lombok.Data;
import java.util.List;

@Data
public class TeaPostVO {
    private Long id;
    private Long userId;
    private String content;
    private List<String> images;
    private Integer likeCount;
    private Integer commentCount;
    private Boolean isLiked;
    private Boolean isFollowing;
    private String createTime;
    private AuthorVO author;
}

