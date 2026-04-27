package com.xytgy.teamallbackend.module.teacircle.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.module.teacircle.service.TeaCampaignService;
import com.xytgy.teamallbackend.module.teacircle.vo.TeaCampaignVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "茶友圈 - 活动")
@RequestMapping("/api/tea-circle/campaigns")
public class TeaCircleCampaignController {

    @Autowired
    private TeaCampaignService teaCampaignService;

    @GetMapping("/latest")
    @Operation(summary = "获取最新的活动 Banner")
    public Result<TeaCampaignVO> getLatestCampaign() {
        return Result.success(teaCampaignService.getLatestCampaign());
    }
}
