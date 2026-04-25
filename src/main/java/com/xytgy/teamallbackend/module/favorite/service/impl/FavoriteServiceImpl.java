package com.xytgy.teamallbackend.module.favorite.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.common.mapstruct.CopyMapper;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.favorite.entity.Favorite;
import com.xytgy.teamallbackend.module.favorite.repository.FavoriteMapper;
import com.xytgy.teamallbackend.module.favorite.service.FavoriteService;
import com.xytgy.teamallbackend.module.product.entity.Product;
import com.xytgy.teamallbackend.module.product.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FavoriteServiceImpl extends ServiceImpl<FavoriteMapper, Favorite> implements FavoriteService {

    @Autowired
    private ProductService productService;

    @Autowired
    private CopyMapper copyMapper;

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