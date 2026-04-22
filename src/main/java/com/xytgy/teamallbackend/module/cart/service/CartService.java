package com.xytgy.teamallbackend.module.cart.service;

import com.xytgy.teamallbackend.module.cart.entity.Cart;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xytgy.teamallbackend.module.cart.vo.CartItemVO;

import java.util.List;

/**
* @author xytgy
* @description 针对表【cart】的数据库操作Service
* @createDate 2026-04-15 08:01:18
*/
public interface CartService extends IService<Cart> {
    CartItemVO addToCart(Long userId, Long productId, Integer quantity);
    List<CartItemVO> listCart(Long userId);
    CartItemVO updateCart(Long userId, Long cartId, Integer quantity);
    void deleteCart(Long userId, Long cartId);
    void removeByUserAndProductIds(Long userId, List<Long> productIds);
}
