package com.xytgy.teamallbackend.module.teacircle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xytgy.teamallbackend.module.teacircle.entity.TeaCampaign;
import com.xytgy.teamallbackend.module.teacircle.vo.TeaCampaignVO;

public interface TeaCampaignService extends IService<TeaCampaign> {
    TeaCampaignVO getLatestCampaign();
}
