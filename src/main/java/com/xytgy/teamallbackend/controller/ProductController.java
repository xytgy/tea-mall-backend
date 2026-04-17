package com.xytgy.teamallbackend.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.service.ProductService;
import com.xytgy.teamallbackend.vo.ProductVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}
