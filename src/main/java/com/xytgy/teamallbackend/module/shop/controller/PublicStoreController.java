package com.xytgy.teamallbackend.module.shop.controller;

import com.xytgy.teamallbackend.common.PageResult;
import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.module.product.service.ProductService;
import com.xytgy.teamallbackend.module.product.vo.ProductVO;
import com.xytgy.teamallbackend.module.shop.service.ShopService;
import com.xytgy.teamallbackend.module.shop.vo.ShopVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "公开店铺接口")
@RequestMapping("/api/store")
@RequiredArgsConstructor
public class PublicStoreController {

    private final ShopService shopService;
    
    private final ProductService productService;

    @GetMapping("/{id}")
    @Operation(summary = "获取公开店铺详情")
    public Result<ShopVO> getStoreDetail(@PathVariable("id") Long shopId) {
        // 这里根据业务需求，可能需要脱敏某些字段
        ShopVO shopVO = shopService.getShopById(shopId);
        return Result.success("获取成功", shopVO);
    }

    @GetMapping("/{id}/products")
    @Operation(summary = "获取公开店铺的商品列表")
    public Result<PageResult<ProductVO>> getStoreProducts(
            @PathVariable("id") Long shopId,
            @org.springframework.web.bind.annotation.RequestParam(value = "page", defaultValue = "1") int page,
            @org.springframework.web.bind.annotation.RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return Result.success(productService.listMerchantProducts(shopId, page, pageSize));
    }

    @GetMapping("/list")
    @Operation(summary = "获取公开店铺列表")
    public Result<PageResult<ShopVO>> listShops(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.success(shopService.listShops(page, pageSize, keyword));
    }
}
