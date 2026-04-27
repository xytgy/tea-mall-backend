package com.xytgy.teamallbackend.module.teacircle.vo;

import lombok.Data;

@Data
public class TeaTopicVO {
    private String id;
    private String title;
    private String description;
    private String viewCount;
    private Integer postCount;
    private Boolean isHot;
}
