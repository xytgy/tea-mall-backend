package com.xytgy.teamallbackend.module.teacircle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("tea_campaign")
public class TeaCampaign {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String cover;
    private String description;
    private String link;
    private Integer status; // 1:进行中, 0:已结束/停用
    private LocalDateTime createTime;
}