package com.xytgy.teamallbackend.module.shop.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xytgy.teamallbackend.common.PageResult;
import com.xytgy.teamallbackend.module.shop.dto.ShopRegisterRequest;
import com.xytgy.teamallbackend.module.shop.dto.ShopUpdateRequest;
import com.xytgy.teamallbackend.module.shop.entity.Shop;
import com.xytgy.teamallbackend.module.shop.vo.ShopVO;

public interface ShopService extends IService<Shop> {
    
    /**
     * 根据用户ID获取店铺ID
     */
    Long getShopIdByUserId(Long userId);

    /**
     * 商家入驻/完善信息
     */
    void registerShop(ShopRegisterRequest request, Long userId);

    /**
     * 获取我的店铺信息
     */
    ShopVO getMyShopInfo(Long userId);

    /**
     * 修改店铺信息
     */
    void updateShop(ShopUpdateRequest request, Long userId);

    /**
     * 根据店铺ID获取公开店铺信息
     */
    ShopVO getShopById(Long shopId);

    PageResult<ShopVO> listShops(int page, int pageSize, String keyword);
}

