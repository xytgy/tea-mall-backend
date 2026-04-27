package com.xytgy.teamallbackend.module.support.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SupportTicketVO {
    private Long id;
    private String category;
    private String title;
    private String content;
    private String contact;
    private String status;
    private String createTime;
    private String reply;
    private String replyTime;
}