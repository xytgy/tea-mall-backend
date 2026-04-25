package com.xytgy.teamallbackend.module.cart.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.common.mapstruct.CopyMapper;
import com.xytgy.teamallbackend.module.cart.entity.Cart;
import com.xytgy.teamallbackend.module.product.entity.Product;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.cart.service.CartService;
import com.xytgy.teamallbackend.module.cart.repository.CartMapper;
import com.xytgy.teamallbackend.module.product.service.ProductService;
import com.xytgy.teamallbackend.module.cart.vo.CartItemVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
/**
* @author xytgy
* @description 针对表【cart】的数据库操作Service实现
* @createDate 2026-04-15 08:01:18
*/
@Service
public class CartServiceImpl extends ServiceImpl<CartMapper, Cart>
    implements CartService{

    @Autowired
    private ProductService productService;

    @Autowired
    private CopyMapper copyMapper;

    @Override
    public CartItemVO addToCart(Long userId, Long productId, Integer quantity) {
        if (productId == null || quantity == null || quantity < 1) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "参数错误");
        }

        Product product = productService.getById(productId);
        if (product == null || !Objects.equals(product.getStatus(), 1)) {
            throw new ServiceException(ResultCode.NOT_FOUND, "商品不存在或已下架");
        }

        Cart existing = lambdaQuery()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getProductId, productId)
                .one();

        int targetQty = quantity;
        if (existing != null) {
            targetQty = existing.getQuantity() + quantity;
        }

        if (targetQty > product.getStock()) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "库存不足");
        }

        if (existing != null) {
            existing.setQuantity(targetQty);
            updateById(existing);
            return toCartItemVO(existing, product);
        }

        Cart cart = new Cart();
        cart.setUserId(userId);
        cart.setProductId(productId);
        cart.setQuantity(quantity);
        save(cart);
        return toCartItemVO(cart, product);
    }

    @Override
    public List<CartItemVO> listCart(Long userId) {
        List<Cart> cartList = lambdaQuery()
                .eq(Cart::getUserId, userId)
                .orderByDesc(Cart::getCreateTime)
                .list();
        if (cartList.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> productIds = cartList.stream().map(Cart::getProductId).collect(Collectors.toSet());
        Map<Long, Product> productMap = productService.listByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        return cartList.stream()
                .map(cart -> toCartItemVO(cart, productMap.get(cart.getProductId())))
                .collect(Collectors.toList());
    }

    @Override
    public CartItemVO updateCart(Long userId, Long cartId, Integer quantity) {
        if (cartId == null || quantity == null || quantity < 1) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "参数错误");
        }

        Cart cart = lambdaQuery()
                .eq(Cart::getId, cartId)
                .eq(Cart::getUserId, userId)
                .one();
        if (cart == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "购物车项不存在");
        }

        Product product = productService.getById(cart.getProductId());
        if (product == null || !Objects.equals(product.getStatus(), 1)) {
            throw new ServiceException(ResultCode.NOT_FOUND, "商品不存在或已下架");
        }
        if (quantity > product.getStock()) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "库存不足");
        }

        cart.setQuantity(quantity);
        updateById(cart);
        return toCartItemVO(cart, product);
    }

    @Override
    public void deleteCart(Long userId, Long cartId) {
        lambdaUpdate()
                .eq(Cart::getId, cartId)
                .eq(Cart::getUserId, userId)
                .remove();
    }   

    @Override
    public void removeByUserAndProductIds(Long userId, List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return;
        }
        lambdaUpdate()
                .eq(Cart::getUserId, userId)
                .in(Cart::getProductId, productIds)
                .remove();
    }

    private CartItemVO toCartItemVO(Cart cart, Product product) {
        if (product == null) {
            return CartItemVO.builder()
                    .id(cart.getId())
                    .productId(cart.getProductId())
                    .name("")
                    .price(null)
                    .quantity(cart.getQuantity())
                    .stock(0)
                    .imageUrl("")
                    .build();
        }
        return copyMapper.toCartItemVO(cart, product);
    }
}



