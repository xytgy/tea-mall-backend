package com.xytgy.teamallbackend.module.shop.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.common.UserContext;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.shop.dto.ShopRegisterRequest;
import com.xytgy.teamallbackend.module.shop.dto.ShopUpdateRequest;
import com.xytgy.teamallbackend.module.shop.service.ShopService;
import com.xytgy.teamallbackend.module.shop.vo.ShopVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@Tag(name = "店铺管理")
@RequestMapping("/api/shop")
public class ShopController {

    @Autowired
    private ShopService shopService;

    @PostMapping("/register")
    @Operation(summary = "商家入驻/完善信息")
    public Result<Void> register(@Validated @RequestBody ShopRegisterRequest request) {
        requireRole(2); // 2: 商家
        Long userId = UserContext.getCurrentUserId();
        shopService.registerShop(request, userId);
        return Result.success("入驻成功", null);
    }

    @GetMapping("/info")
    @Operation(summary = "获取我的店铺信息")
    public Result<ShopVO> getInfo() {
        requireRole(2);
        Long userId = UserContext.getCurrentUserId();
        ShopVO shopVO = shopService.getMyShopInfo(userId);
        return Result.success("获取成功", shopVO);
    }

    @PutMapping("/update")
    @Operation(summary = "修改店铺信息")
    public Result<Void> update(@Validated @RequestBody ShopUpdateRequest request) {
        requireRole(2);
        Long userId = UserContext.getCurrentUserId();
        shopService.updateShop(request, userId);
        return Result.success("更新成功", null);
    }

    private void requireRole(Integer expectRole) {
        Map<String, Object> user = UserContext.getUser();
        Integer role = null;
        if (user != null && user.get("role") != null) {
            role = Integer.valueOf(String.valueOf(user.get("role")));
        }
        if (!expectRole.equals(role)) {
            throw new ServiceException(ResultCode.FORBIDDEN, "无权限访问");
        }
    }
}
