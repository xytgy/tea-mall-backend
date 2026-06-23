package com.xytgy.teamallbackend.module.banner.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.module.banner.entity.Banner;
import com.xytgy.teamallbackend.module.banner.service.BannerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/banner")
@Tag(name = "轮播图")
@SecurityRequirement(name = "BearerAuth")
@RequiredArgsConstructor
public class BannerController {

    private final BannerService bannerService;

    @GetMapping("/list")
    @Operation(summary = "获取轮播图列表")
    public Result<List<Banner>> list() {
        return Result.success(bannerService.listActiveBanners());
    }
}
