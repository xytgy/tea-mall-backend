package com.xytgy.teamallbackend.module.product.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.module.product.service.ProductCategoryService;
import com.xytgy.teamallbackend.module.product.vo.CategoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/category")
@Tag(name = "商品分类")
@SecurityRequirement(name = "BearerAuth")
@RequiredArgsConstructor
public class ProductCategoryController {

    private final ProductCategoryService productCategoryService;

    @GetMapping("/list")
    @Operation(summary = "获取分类列表", description = "获取所有启用的商品分类，按排序值降序")
    public Result<List<CategoryVO>> list() {
        return Result.success(productCategoryService.listActiveCategories());
    }
}
