package com.xytgy.teamallbackend.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.common.UserContext;
import com.xytgy.teamallbackend.dto.CartAddRequest;
import com.xytgy.teamallbackend.dto.CartDeleteRequest;
import com.xytgy.teamallbackend.dto.CartUpdateRequest;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.service.CartService;
import com.xytgy.teamallbackend.vo.CartItemVO;
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
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "成功",
                    content = @Content(examples = @ExampleObject(value = "{\"code\":200,\"message\":\"成功\",\"data\":{\"id\":10,\"productId\":1,\"productName\":\"茉莉绿茶\",\"productPrice\":12.00,\"quantity\":2,\"imageUrl\":\"https://example.com/p1.jpg\"}}"))
            ),
            @ApiResponse(responseCode = "400", description = "参数错误或库存不足"),
            @ApiResponse(responseCode = "401", description = "未登录")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    schema = @Schema(implementation = CartAddRequest.class),
                    examples = @ExampleObject(value = "{\"productId\":1,\"quantity\":1}")
            )
    )
    public Result<Void> add(@RequestBody CartAddRequest request) {
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

    @PutMapping("/update")
    @Operation(summary = "修改购物车商品数量")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "成功",
                    content = @Content(examples = @ExampleObject(value = "{\"code\":200,\"message\":\"成功\",\"data\":null}"))
            ),
            @ApiResponse(responseCode = "400", description = "参数错误或库存不足"),
            @ApiResponse(responseCode = "404", description = "购物车项不存在"),
            @ApiResponse(responseCode = "401", description = "未登录")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    schema = @Schema(implementation = CartUpdateRequest.class),
                    examples = @ExampleObject(value = "{\"id\":10,\"quantity\":2}")
            )
    )
    public Result<Void> update(@RequestBody CartUpdateRequest request) {
        Long userId = currentUserId();
        cartService.updateCart(userId, request.getId(), request.getQuantity());
        return Result.success(null);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "批量删除购物车项")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "成功",
                    content = @Content(examples = @ExampleObject(value = "{\"code\":200,\"message\":\"成功\",\"data\":null}"))
            ),
            @ApiResponse(responseCode = "401", description = "未登录")
    })
    public Result<Void> delete(@RequestBody CartDeleteRequest request) {
        Long userId = currentUserId();
        if (request != null && request.getIds() != null && !request.getIds().isEmpty()) {
            for (Long id : request.getIds()) {
                cartService.deleteCart(userId, id);
            }
        }
        return Result.success(null);
    }

    private Long currentUserId() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new ServiceException(401, "未登录");
        }
        return userId;
    }
}
