package com.xytgy.teamallbackend.module.product.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.common.UserContext;
import com.xytgy.teamallbackend.module.product.dto.MerchantGoodsAddRequest;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.product.service.ProductService;
import com.xytgy.teamallbackend.module.product.vo.IdVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "商家")
@RequestMapping("/api/merchant/goods")
@RequiredArgsConstructor
public class MerchantGoodsController {

    private final ProductService productService;

    @PostMapping("/add")
    public Result<IdVO> add(@RequestBody MerchantGoodsAddRequest request) {
        requireRole(1);
        Long shopId = currentShopId();
        Long id = productService.addMerchantGoods(shopId, request);
        return Result.success("新商品发布成功", new IdVO(id));
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
        Integer role = UserContext.getRole();
        if (!expectRole.equals(role)) {
            throw new ServiceException(ResultCode.FORBIDDEN, "无权限访问");
        }
    }
}
