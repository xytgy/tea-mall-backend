package com.xytgy.teamallbackend.module.shop.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.module.shop.service.ShopService;
import com.xytgy.teamallbackend.module.shop.vo.ShopVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "公开店铺接口")
@RequestMapping("/api/store")
public class PublicStoreController {

    @Autowired
    private ShopService shopService;

    @GetMapping("/{id}")
    @Operation(summary = "获取公开店铺详情")
    public Result<ShopVO> getStoreDetail(@PathVariable("id") Long shopId) {
        // 这里根据业务需求，可能需要脱敏某些字段
        ShopVO shopVO = shopService.getShopById(shopId);
        return Result.success("获取成功", shopVO);
    }
}
