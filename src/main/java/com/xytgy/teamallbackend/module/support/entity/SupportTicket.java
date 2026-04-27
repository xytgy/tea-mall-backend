package com.xytgy.teamallbackend.module.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("support_ticket")
public class SupportTicket {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String category;
    private String title;
    private String content;
    private String contact;
    private String status;
    private LocalDateTime createTime;
    private String replyContent;
    private LocalDateTime replyTime;
}