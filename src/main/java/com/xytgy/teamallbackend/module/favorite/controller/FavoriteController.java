package com.xytgy.teamallbackend.module.favorite.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.common.UserContext;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.favorite.dto.FavoriteAddRequest;
import com.xytgy.teamallbackend.module.favorite.dto.FavoriteRemoveRequest;
import com.xytgy.teamallbackend.module.favorite.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/user/favorites")
@Tag(name = "商品收藏")
@SecurityRequirement(name = "BearerAuth")
public class FavoriteController {

    @Autowired
    private FavoriteService favoriteService;

    @GetMapping("/list")
    @Operation(summary = "获取收藏列表")
    public Result<java.util.List<com.xytgy.teamallbackend.module.favorite.vo.FavoriteItemVO>> listFavorites() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new ServiceException(ResultCode.UNAUTHORIZED, "未登录");
        }
        return Result.success(favoriteService.listFavorites(userId));
    }

    @PostMapping("/add")
    @Operation(summary = "添加商品到收藏")
    public Result<Void> addFavorite(@Validated @RequestBody FavoriteAddRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new ServiceException(ResultCode.UNAUTHORIZED, "未登录");
        }
        favoriteService.addFavorite(userId, request.getProductId());
        return Result.success("收藏成功", null);
    }

    @PostMapping("/remove")
    @Operation(summary = "取消商品收藏")
    public Result<Void> removeFavorite(@Validated @RequestBody FavoriteRemoveRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new ServiceException(ResultCode.UNAUTHORIZED, "未登录");
        }
        favoriteService.removeFavorite(userId, request.getProductId());
        return Result.success("取消收藏成功", null);
    }

    @GetMapping("/check")
    @Operation(summary = "检查商品是否已收藏")
    public Result<Boolean> checkFavorite(@RequestParam("productId") Long productId) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new ServiceException(ResultCode.UNAUTHORIZED, "未登录");
        }
        boolean isFavorited = favoriteService.checkFavorite(userId, productId);
        return Result.success(isFavorited);
    }
    
}