package com.xytgy.teamallbackend.module.teacircle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.module.teacircle.entity.TeaCampaign;
import com.xytgy.teamallbackend.module.teacircle.repository.TeaCampaignMapper;
import com.xytgy.teamallbackend.module.teacircle.service.TeaCampaignService;
import com.xytgy.teamallbackend.module.teacircle.vo.TeaCampaignVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

@Service
public class TeaCampaignServiceImpl extends ServiceImpl<TeaCampaignMapper, TeaCampaign> implements TeaCampaignService {

    @Override
    public TeaCampaignVO getLatestCampaign() {
        TeaCampaign campaign = this.getOne(new LambdaQueryWrapper<TeaCampaign>()
                .eq(TeaCampaign::getStatus, 1)
                .orderByDesc(TeaCampaign::getCreateTime)
                .last("LIMIT 1"));

        if (campaign == null) {
            return null;
        }

        return TeaCampaignVO.builder()
                .id(String.valueOf(campaign.getId()))
                .title(campaign.getTitle())
                .cover(campaign.getCover())
                .description(campaign.getDescription())
                .link(campaign.getLink())
                .build();
    }
}
