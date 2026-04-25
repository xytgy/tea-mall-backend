package com.xytgy.teamallbackend.module.teacircle.dto;

import lombok.Data;

@Data
public class TeaCommentAddRequest {
    private String content;
    private Long rootId;
    private Long parentId;
    private Long replyToUserId;
}
