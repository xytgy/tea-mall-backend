package com.xytgy.teamallbackend.module.shop.controller;

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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

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
    public Result<List<ProductVO>> getStoreProducts(@PathVariable("id") Long shopId) {
        // 获取该商家的商品列表，且仅展示上架且审核通过的商品
        List<ProductVO> products = productService.listMerchantProducts(shopId).stream()
                .filter(p -> p.getStatus() != null && p.getStatus() == 1) // 假设 1 为正常上架
                .collect(Collectors.toList());
        return Result.success("获取成功", products);
    }
}
