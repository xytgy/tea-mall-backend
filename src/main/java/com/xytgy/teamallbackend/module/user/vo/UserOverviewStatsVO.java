package com.xytgy.teamallbackend.module.user.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserOverviewStatsVO {
    private Integer favorites;
    private Integer orders;
    private Integer consults;
}