package com.xytgy.teamallbackend.module.favorite.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xytgy.teamallbackend.module.favorite.entity.Favorite;
import com.xytgy.teamallbackend.module.favorite.vo.FavoriteItemVO;

import java.util.List;

public interface FavoriteService extends IService<Favorite> {
    void addFavorite(Long userId, Long productId);
    void removeFavorite(Long userId, Long productId);
    boolean checkFavorite(Long userId, Long productId);
    List<FavoriteItemVO> listFavorites(Long userId);
}
