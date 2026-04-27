package com.xytgy.teamallbackend.module.feedback.dto;

import lombok.Data;
import java.util.List;

@Data
public class FeedbackSubmitRequest {
    private String type;
    private String content;
    private List<String> images;
    private String contact;
}
