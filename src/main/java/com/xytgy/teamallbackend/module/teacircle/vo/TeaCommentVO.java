package com.xytgy.teamallbackend.module.teacircle.vo;

import lombok.Data;
import java.util.List;

@Data
public class TeaCommentVO {
    private Long id;
    private Long postId;
    private Long userId;
    private String content;
    private Long rootId;
    private Long parentId;
    private Long replyToUserId;
    private String createTime;
    private AuthorVO author;
    private AuthorVO replyToUser;
    
    private List<TeaCommentVO> children;
}

