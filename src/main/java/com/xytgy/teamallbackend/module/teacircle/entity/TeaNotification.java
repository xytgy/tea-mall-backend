package com.xytgy.teamallbackend.module.teacircle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("tea_notification")
public class TeaNotification {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId; // 接收者
    private String type; // like, comment, follow
    private Long sourceId; // 来源业务ID(评论id, 点赞id, 关注id)
    private Long actorId; // 触发者
    private Integer isRead; // 0未读 1已读
    private LocalDateTime createTime;
}
