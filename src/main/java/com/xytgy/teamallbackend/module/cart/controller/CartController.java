package com.xytgy.teamallbackend.module.cart.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.common.UserContext;
import com.xytgy.teamallbackend.module.cart.dto.CartAddRequest;
import com.xytgy.teamallbackend.module.cart.dto.CartDeleteRequest;
import com.xytgy.teamallbackend.module.cart.dto.CartUpdateRequest;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.cart.service.CartService;
import com.xytgy.teamallbackend.module.cart.vo.CartItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
@Tag(name = "购物车")
@SecurityRequirement(name = "BearerAuth")
public class CartController {

    @Autowired
    private CartService cartService;

    @PostMapping("/add")
    @Operation(summary = "加入购物车")
    public Result<Void> add(@org.springframework.web.bind.annotation.RequestBody CartAddRequest request) {
        Long userId = currentUserId();
        cartService.addToCart(userId, request.getProductId(), request.getQuantity());
        return Result.success(null);
    }

    @GetMapping("/list")
    @Operation(summary = "购物车列表")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "成功",
                    content = @Content(examples = @ExampleObject(value = "{\"code\":200,\"message\":\"成功\",\"data\":[{\"id\":10,\"productId\":1,\"productName\":\"茉莉绿茶\",\"productPrice\":12.00,\"quantity\":2,\"imageUrl\":\"https://example.com/p1.jpg\"}]}"))
            ),
            @ApiResponse(responseCode = "401", description = "未登录")
    })
    public Result<List<CartItemVO>> list() {
        Long userId = currentUserId();
        return Result.success(cartService.listCart(userId));
    }

    @RequestMapping(value = "/update", method = {RequestMethod.PUT, RequestMethod.POST})
    @Operation(summary = "修改购物车商品数量")
    public Result<Void> update(@org.springframework.web.bind.annotation.RequestBody CartUpdateRequest request) {
        Long userId = currentUserId();
        cartService.updateCart(userId, request.getId(), request.getQuantity());
        return Result.success(null);
    }

    @RequestMapping(value = "/delete", method = {RequestMethod.DELETE, RequestMethod.POST})
    @Operation(summary = "批量删除购物车项")
    public Result<Void> delete(@org.springframework.web.bind.annotation.RequestBody CartDeleteRequest request) {
        Long userId = currentUserId();
        if (request != null && request.getIds() != null && !request.getIds().isEmpty()) {
            for (Long id : request.getIds()) {
                cartService.deleteCart(userId, id);
            }
        }
        return Result.success(null);
    }

    @RequestMapping(value = "/delete/{id}", method = {RequestMethod.DELETE, RequestMethod.GET, RequestMethod.POST})
    @Operation(summary = "删除单个购物车项(兼容路径传参)")
    public Result<Void> deleteById(@PathVariable Long id) {
        Long userId = currentUserId();
        cartService.deleteCart(userId, id);
        return Result.success(null);
    }

    private Long currentUserId() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new ServiceException(ResultCode.UNAUTHORIZED, "未登录");
        }
        return userId;
    }
}
