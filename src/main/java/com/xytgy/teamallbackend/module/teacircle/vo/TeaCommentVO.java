package com.xytgy.teamallbackend.module.teacircle.vo;

import lombok.Data;
import java.util.List;

@Data
public class TeaCommentVO {
    private Long id;
    private Long postId;
    private Long userId;
    private String userAccount;
    private String nickname;
    private String avatar;
    private String content;
    private Long rootId;
    private Long parentId;
    private Long replyToUserId;
    private String replyToUserNickname;
    private String createTime;
    
    // 如果是父评论，这里可以包含子评论（限制返回几条或者全量返回，视具体实现而定）
    private List<TeaCommentVO> replies;
}
