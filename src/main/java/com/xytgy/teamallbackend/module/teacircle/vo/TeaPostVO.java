package com.xytgy.teamallbackend.module.teacircle.vo;

import lombok.Data;
import java.util.List;

@Data
public class TeaPostVO {
    private Long id;
    private Long userId;
    private String content;
    private List<String> images;
    private List<String> topics;
    private Integer likeCount;
    private Integer commentCount;
    private Integer status;
    private Boolean isLiked;
    private Boolean isFollowing;
    private String createTime;
    private String updateTime;
    private AuthorVO author;
    
    private List<TeaCommentVO> comments;
}

