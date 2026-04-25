package com.xytgy.teamallbackend.module.teacircle.vo;

import lombok.Data;

@Data
public class TeaNotificationVO {
    private Long id;
    private Long actorId;
    private String actorNickname;
    private String actorAvatar;
    private String type; // like, comment, follow
    private Long sourceId;
    private String sourceContent; // 例如评论内容，或被点赞的动态摘要
    private Integer isRead;
    private String createTime;
}
