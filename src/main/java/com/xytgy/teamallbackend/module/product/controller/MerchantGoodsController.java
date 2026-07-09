package com.xytgy.teamallbackend.module.product.controller;

import com.xytgy.teamallbackend.common.BaseController;
import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.module.product.dto.MerchantGoodsAddRequest;
import jakarta.validation.Valid;
import com.xytgy.teamallbackend.module.product.service.ProductService;
import com.xytgy.teamallbackend.module.product.vo.IdVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "商家")
@RequestMapping("/api/merchant/goods")
@Validated
@RequiredArgsConstructor
public class MerchantGoodsController extends BaseController {

    private final ProductService productService;

    @PreAuthorize("hasRole('MERCHANT')")
    @PostMapping("/add")
    @Operation(summary = "新增商品")
    public Result<IdVO> add(@Valid @RequestBody MerchantGoodsAddRequest request) {
        Long shopId = currentShopId();
        Long id = productService.addMerchantGoods(shopId, request);
        return Result.success("新商品发布成功", new IdVO(id));
    }

}
