package com.xytgy.teamallbackend.module.teacircle.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TeaTopicVO {
    private String id;
    private String title;
    private String description;
    private String viewCount;
    private Long postCount;
    private Boolean isHot;
}
