package com.xytgy.teamallbackend.module.feedback.vo;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class FeedbackVO {
    private Long id;
    private Long userId;
    private String type;
    private String content;
    private List<String> images;
    private String contact;
    private Integer status;
    private String createTime;
}