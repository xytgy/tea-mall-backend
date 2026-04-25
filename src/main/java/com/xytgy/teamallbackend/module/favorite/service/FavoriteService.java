package com.xytgy.teamallbackend.module.favorite.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xytgy.teamallbackend.module.favorite.entity.Favorite;

public interface FavoriteService extends IService<Favorite> {
    void addFavorite(Long userId, Long productId);
    void removeFavorite(Long userId, Long productId);
    boolean checkFavorite(Long userId, Long productId);
}