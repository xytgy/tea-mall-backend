package com.xytgy.teamallbackend.module.product.controller;

import com.xytgy.teamallbackend.common.BaseController;
import com.xytgy.teamallbackend.common.PageResult;
import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.annotation.BrowserCache;
import com.xytgy.teamallbackend.annotation.CacheStrategy;
import com.xytgy.teamallbackend.module.product.cache.ProductLastModifiedProvider;
import com.xytgy.teamallbackend.module.product.dto.ProductAddRequest;
import com.xytgy.teamallbackend.module.product.dto.ProductAuditRequest;
import com.xytgy.teamallbackend.module.product.dto.ProductStatusRequest;
import com.xytgy.teamallbackend.module.product.dto.ProductUpdateRequest;
import com.xytgy.teamallbackend.module.product.service.ProductService;
import com.xytgy.teamallbackend.module.product.vo.AuditVO;
import com.xytgy.teamallbackend.module.product.vo.ProductVO;
import com.xytgy.teamallbackend.module.product.vo.ProductReviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/product")
@Tag(name = "商品")
@SecurityRequirement(name = "BearerAuth")
@Validated
@RequiredArgsConstructor
public class ProductController extends BaseController {

    private final ProductService productService;

    @GetMapping("/list")
    @Operation(summary = "商品列表", description = "返回当前可售商品列表")
    @ApiResponse(
            responseCode = "200",
            description = "成功",
            content = @Content(examples = @ExampleObject(value = "{\"code\":200,\"message\":\"成功\",\"data\":[{\"id\":1,\"name\":\"茉莉绿茶\",\"price\":12.00,\"stock\":100,\"imageUrl\":\"https://example.com/p1.jpg\"}]}"))
    )
    @ApiResponse(responseCode = "401", description = "未登录")
    public Result<PageResult<ProductVO>> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "sort", defaultValue = "default") String sort) {
        return Result.success(productService.listProducts(page, pageSize, keyword, category, sort));
    }

    @GetMapping("/{id}")
    @Operation(summary = "商品详情", description = "根据商品ID获取商品详情")
    @BrowserCache(
            strategy = CacheStrategy.PRODUCT_DETAIL,
            lastModifiedProvider = ProductLastModifiedProvider.class
    )
    public Result<ProductVO> detail(@PathVariable("id") Long id) {
        return Result.success(productService.getProductDetail(id));
    }

    @PreAuthorize("hasRole('MERCHANT')")
    @GetMapping("/merchant/list")
    @Operation(summary = "商家获取自己的商品列表")
    public Result<PageResult<ProductVO>> merchantList(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        Long shopId = currentShopId();
        return Result.success(productService.listMerchantProducts(shopId, page, pageSize));
    }

    @PreAuthorize("hasRole('MERCHANT')")
    @PostMapping("/add")
    @Operation(summary = "商家发布新商品")
    public Result<Void> add(@Valid @RequestBody ProductAddRequest request) {
        Long shopId = currentShopId();
        productService.addProduct(shopId, request);
        return Result.success(null);
    }

    @PreAuthorize("hasRole('MERCHANT')")
    @PutMapping("/update")
    @Operation(summary = "商家编辑商品")
    public Result<Void> update(@Valid @RequestBody ProductUpdateRequest request) {
        Long shopId = currentShopId();
        productService.updateProduct(shopId, request);
        return Result.success(null);
    }

    @PreAuthorize("hasRole('MERCHANT')")
    @PutMapping("/status")
    @Operation(summary = "商家上架/下架商品")
    public Result<Void> updateStatus(@Valid @RequestBody ProductStatusRequest request) {
        Long shopId = currentShopId();
        productService.updateProductStatus(shopId, request);
        return Result.success(null);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/audit/list")
    @Operation(summary = "管理员获取待审核商品列表")
    public Result<List<AuditVO>> auditList() {
        return Result.success(productService.listPendingAuditProducts());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/audit")
    @Operation(summary = "管理员审核商品")
    public Result<Void> audit(@Valid @RequestBody ProductAuditRequest request) {
        productService.auditProduct(request);
        return Result.success(null);
    }

    @GetMapping("/reviews")
    @Operation(summary = "获取商品评价列表")
    public Result<List<ProductReviewVO>> getProductReviews(@RequestParam("productId") Long productId) {
        return Result.success(productService.listProductReviews(productId));
    }

}
