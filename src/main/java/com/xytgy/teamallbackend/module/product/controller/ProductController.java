package com.xytgy.teamallbackend.module.product.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.common.UserContext;
import com.xytgy.teamallbackend.module.product.dto.ProductAddRequest;
import com.xytgy.teamallbackend.module.product.dto.ProductAuditRequest;
import com.xytgy.teamallbackend.module.product.dto.ProductStatusRequest;
import com.xytgy.teamallbackend.module.product.dto.ProductUpdateRequest;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.product.service.ProductService;
import com.xytgy.teamallbackend.module.product.vo.AuditVO;
import com.xytgy.teamallbackend.module.product.vo.ProductVO;
import com.xytgy.teamallbackend.module.product.vo.ProductReviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/product")
@Tag(name = "商品")
@SecurityRequirement(name = "BearerAuth")
public class ProductController {

    @Autowired
    private ProductService productService;

    @GetMapping("/list")
    @Operation(summary = "商品列表", description = "返回当前可售商品列表")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "成功",
                    content = @Content(examples = @ExampleObject(value = "{\"code\":200,\"message\":\"成功\",\"data\":[{\"id\":1,\"name\":\"茉莉绿茶\",\"price\":12.00,\"stock\":100,\"imageUrl\":\"https://example.com/p1.jpg\"}]}"))
            ),
            @ApiResponse(responseCode = "401", description = "未登录")
    })
    public Result<List<ProductVO>> list() {
        return Result.success(productService.listAvailableProducts());
    }

    @GetMapping("/merchant/list")
    @Operation(summary = "商家获取自己的商品列表")
    public Result<List<ProductVO>> merchantList() {
        requireRole(1);
        Long shopId = currentShopId();
        return Result.success(productService.listMerchantProducts(shopId));
    }

    @PostMapping("/add")
    @Operation(summary = "商家发布新商品")
    public Result<Void> add(@RequestBody ProductAddRequest request) {
        requireRole(1);
        Long shopId = currentShopId();
        productService.addProduct(shopId, request);
        return Result.success(null);
    }

    @PutMapping("/update")
    @Operation(summary = "商家编辑商品")
    public Result<Void> update(@RequestBody ProductUpdateRequest request) {
        requireRole(1);
        Long shopId = currentShopId();
        productService.updateProduct(shopId, request);
        return Result.success(null);
    }

    @PutMapping("/status")
    @Operation(summary = "商家上架/下架商品")
    public Result<Void> updateStatus(@RequestBody ProductStatusRequest request) {
        requireRole(1);
        Long shopId = currentShopId();
        productService.updateProductStatus(shopId, request);
        return Result.success(null);
    }

    @GetMapping("/audit/list")
    @Operation(summary = "管理员获取待审核商品列表")
    public Result<List<AuditVO>> auditList() {
        requireRole(2);
        return Result.success(productService.listPendingAuditProducts());
    }

    @PutMapping("/audit")
    @Operation(summary = "管理员审核商品")
    public Result<Void> audit(@RequestBody ProductAuditRequest request) {
        requireRole(2);
        productService.auditProduct(request);
        return Result.success(null);
    }

    @GetMapping("/reviews")
    @Operation(summary = "获取商品评价列表")
    public Result<List<ProductReviewVO>> getProductReviews(@RequestParam("productId") Long productId) {
        return Result.success(productService.listProductReviews(productId));
    }

    private Long currentUserId() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new ServiceException(ResultCode.UNAUTHORIZED, "未登录");
        }
        return userId;
    }

    private Long currentShopId() {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            throw new ServiceException(ResultCode.FORBIDDEN, "请先完善店铺信息");
        }
        return shopId;
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
