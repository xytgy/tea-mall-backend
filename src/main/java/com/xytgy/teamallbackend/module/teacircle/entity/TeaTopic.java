package com.xytgy.teamallbackend.module.teacircle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("tea_topic")
public class TeaTopic {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String description;
    private Integer participantsCount;
    private Integer postsCount;
    private Integer status;
    private LocalDateTime createTime;
}
