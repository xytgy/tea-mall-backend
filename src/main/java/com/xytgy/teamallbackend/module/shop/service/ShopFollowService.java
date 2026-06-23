package com.xytgy.teamallbackend.module.shop.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xytgy.teamallbackend.module.shop.entity.ShopFollow;

import java.util.Map;

public interface ShopFollowService extends IService<ShopFollow> {
    Map<String, Boolean> toggleFollow(Long userId, Long shopId);
    boolean isFollowing(Long userId, Long shopId);
    long countFollowers(Long shopId);
}