package com.xytgy.teamallbackend.module.favorite.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.common.mapstruct.CopyMapper;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.favorite.entity.Favorite;
import com.xytgy.teamallbackend.module.favorite.repository.FavoriteMapper;
import com.xytgy.teamallbackend.module.favorite.service.FavoriteService;
import com.xytgy.teamallbackend.module.favorite.vo.FavoriteItemVO;
import com.xytgy.teamallbackend.module.product.entity.Product;
import com.xytgy.teamallbackend.module.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl extends ServiceImpl<FavoriteMapper, Favorite> implements FavoriteService {

    private final ProductService productService;
    private final CopyMapper copyMapper;

    @Override
    public List<FavoriteItemVO> listFavorites(Long userId) {
        if (userId == null) {
            throw new ServiceException(ResultCode.UNAUTHORIZED, "未登录");
        }
        
        List<Favorite> favorites = lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .orderByDesc(Favorite::getCreateTime)
                .list();
                
        if (favorites.isEmpty()) {
            return Collections.emptyList();
        }
        
        Set<Long> productIds = favorites.stream().map(Favorite::getProductId).collect(Collectors.toSet());
        Map<Long, Product> productMap = productService.listByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
                
        return favorites.stream().map(f -> {
            Product p = productMap.get(f.getProductId());
            FavoriteItemVO vo = new FavoriteItemVO();
            vo.setId(f.getId());
            vo.setProductId(f.getProductId());
            if (p != null) {
                vo.setName(p.getName());
                vo.setPrice(p.getPrice());
                vo.setImageUrl(p.getImageUrl());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public void addFavorite(Long userId, Long productId) {
        if (userId == null || productId == null) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "参数错误");
        }
        
        // 校验商品是否存在
        Product product = productService.getById(productId);
        if (product == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "商品不存在");
        }

        // 检查是否已经收藏过
        long count = lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getProductId, productId)
                .count();
        
        if (count > 0) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "已经收藏过了");
        }

        Favorite favorite = copyMapper.toFavorite(userId, productId);
        save(favorite);
    }

    @Override
    public void removeFavorite(Long userId, Long productId) {
        if (userId == null || productId == null) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "参数错误");
        }
        
        lambdaUpdate()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getProductId, productId)
                .remove();
    }

    @Override
    public boolean checkFavorite(Long userId, Long productId) {
        if (userId == null || productId == null) {
            return false;
        }
        long count = lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getProductId, productId)
                .count();
        return count > 0;
    }
}
