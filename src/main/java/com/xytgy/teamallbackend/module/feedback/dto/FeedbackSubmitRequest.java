package com.xytgy.teamallbackend.module.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class FeedbackSubmitRequest {
    private String type;
    @NotBlank(message = "反馈内容不能为空")
    private String content;
    private List<String> images;
    private String contact;
}
