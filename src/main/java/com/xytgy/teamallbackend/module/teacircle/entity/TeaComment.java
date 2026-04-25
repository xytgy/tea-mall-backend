package com.xytgy.teamallbackend.module.teacircle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("tea_comment")
public class TeaComment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private Long userId;
    private String content;
    private Long rootId;
    private Long parentId;
    private Long replyToUserId;
    private LocalDateTime createTime;
    private Integer isDeleted;
}
