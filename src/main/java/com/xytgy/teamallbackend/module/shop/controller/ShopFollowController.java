package com.xytgy.teamallbackend.module.shop.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.security.SecurityUtils;
import com.xytgy.teamallbackend.module.shop.service.ShopFollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@Tag(name = "店铺关注")
@RequestMapping("/api/shop")
@RequiredArgsConstructor
public class ShopFollowController {

    private final ShopFollowService shopFollowService;

    @PostMapping("/{shopId}/follow")
    @Operation(summary = "关注/取消关注店铺")
    public Result<Map<String, Boolean>> toggleFollow(@PathVariable Long shopId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(shopFollowService.toggleFollow(userId, shopId));
    }

    @GetMapping("/{shopId}/followers")
    @Operation(summary = "获取店铺粉丝数")
    public Result<Map<String, Long>> getFollowersCount(@PathVariable Long shopId) {
        long count = shopFollowService.countFollowers(shopId);
        Map<String, Long> result = new HashMap<>();
        result.put("count", count);
        return Result.success(result);
    }
}