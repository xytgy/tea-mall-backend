package com.xytgy.teamallbackend.module.shop.controller;

import com.xytgy.teamallbackend.common.BaseController;
import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.security.SecurityUtils;
import com.xytgy.teamallbackend.module.shop.dto.ShopRegisterRequest;
import com.xytgy.teamallbackend.module.shop.dto.ShopUpdateRequest;
import com.xytgy.teamallbackend.module.shop.service.ShopService;
import com.xytgy.teamallbackend.module.shop.vo.ShopVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "店铺管理")
@RequestMapping("/api/shop")
@RequiredArgsConstructor
public class ShopController extends BaseController {

    private final ShopService shopService;

    @PreAuthorize("hasRole('MERCHANT')")
    @PostMapping("/register")
    @Operation(summary = "商家入驻/完善信息")
    public Result<Void> register(@Validated @RequestBody ShopRegisterRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        shopService.registerShop(request, userId);
        return Result.success("入驻成功", null);
    }

    @PreAuthorize("hasRole('MERCHANT')")
    @GetMapping("/info")
    @Operation(summary = "获取我的店铺信息")
    public Result<ShopVO> getInfo() {
        Long userId = SecurityUtils.getCurrentUserId();
        ShopVO shopVO = shopService.getMyShopInfo(userId);
        return Result.success("获取成功", shopVO);
    }

    @PreAuthorize("hasRole('MERCHANT')")
    @PutMapping("/update")
    @Operation(summary = "修改店铺信息")
    public Result<Void> update(@Validated @RequestBody ShopUpdateRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        shopService.updateShop(request, userId);
        return Result.success("更新成功", null);
    }

}
