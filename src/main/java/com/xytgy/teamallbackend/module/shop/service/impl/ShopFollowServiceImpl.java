package com.xytgy.teamallbackend.module.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.module.shop.entity.Shop;
import com.xytgy.teamallbackend.module.shop.entity.ShopFollow;
import com.xytgy.teamallbackend.module.shop.mapper.ShopFollowMapper;
import com.xytgy.teamallbackend.module.shop.service.ShopFollowService;
import com.xytgy.teamallbackend.module.shop.service.ShopService;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ShopFollowServiceImpl extends ServiceImpl<ShopFollowMapper, ShopFollow>
        implements ShopFollowService {

    private final ShopService shopService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Boolean> toggleFollow(Long userId, Long shopId) {
        Shop shop = shopService.getById(shopId);
        if (shop == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "店铺不存在");
        }

        LambdaQueryWrapper<ShopFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ShopFollow::getUserId, userId)
               .eq(ShopFollow::getShopId, shopId);
        ShopFollow existing = this.getOne(wrapper);

        boolean isFollowing;
        if (existing != null) {
            this.removeById(existing.getId());
            isFollowing = false;
        } else {
            ShopFollow follow = new ShopFollow();
            follow.setUserId(userId);
            follow.setShopId(shopId);
            this.save(follow);
            isFollowing = true;
        }

        Map<String, Boolean> result = new HashMap<>();
        result.put("isFollowing", isFollowing);
        return result;
    }

    @Override
    public boolean isFollowing(Long userId, Long shopId) {
        if (userId == null || shopId == null) return false;
        return this.count(new LambdaQueryWrapper<ShopFollow>()
                .eq(ShopFollow::getUserId, userId)
                .eq(ShopFollow::getShopId, shopId)) > 0;
    }

    @Override
    public long countFollowers(Long shopId) {
        return this.count(new LambdaQueryWrapper<ShopFollow>()
                .eq(ShopFollow::getShopId, shopId));
    }
}
