package com.xytgy.teamallbackend.module.teacircle.vo;

import lombok.Data;

@Data
public class TeaTopicVO {
    private Long id;
    private String title;
    private String description;
    private Integer participantsCount;
    private Integer postsCount;
    private String createTime;
}
