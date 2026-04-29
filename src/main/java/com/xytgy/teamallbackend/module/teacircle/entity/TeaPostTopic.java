package com.xytgy.teamallbackend.module.teacircle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tea_post_topic")
public class TeaPostTopic {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private Long topicId;
    private LocalDateTime createTime;
}

