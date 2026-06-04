package com.xytgy.teamallbackend.module.teacircle.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TeaCommentAddRequest {
    @NotBlank(message = "评论内容不能为空")
    private String content;
    private Long rootId;
    private Long parentId;
    private Long replyToUserId;
}
