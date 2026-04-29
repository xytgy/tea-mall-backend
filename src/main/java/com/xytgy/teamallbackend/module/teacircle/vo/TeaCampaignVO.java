package com.xytgy.teamallbackend.module.teacircle.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TeaCampaignVO {
    private String id;
    private String title;
    private String cover;
    private String description;
    private String link;
}