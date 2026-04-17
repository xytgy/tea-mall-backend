package com.xytgy.teamallbackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.entity.Cart;
import com.xytgy.teamallbackend.entity.Product;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.service.CartService;
import com.xytgy.teamallbackend.mapper.CartMapper;
import com.xytgy.teamallbackend.service.ProductService;
import com.xytgy.teamallbackend.vo.CartItemVO;
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

    @Override
    public CartItemVO addToCart(Long userId, Long productId, Integer quantity) {
        if (productId == null || quantity == null || quantity < 1) {
            throw new ServiceException(400, "参数错误");
        }

        Product product = productService.getById(productId);
        if (product == null || !Objects.equals(product.getStatus(), 1)) {
            throw new ServiceException(404, "商品不存在或已下架");
        }

        Cart existing = lambdaQuery()
                .eq(Cart::getUser_id, userId)
                .eq(Cart::getProduct_id, productId)
                .one();

        int targetQty = quantity;
        if (existing != null) {
            targetQty = existing.getQuantity() + quantity;
        }

        if (targetQty > product.getStock()) {
            throw new ServiceException(400, "库存不足");
        }

        if (existing != null) {
            existing.setQuantity(targetQty);
            updateById(existing);
            return toCartItemVO(existing, product);
        }

        Cart cart = new Cart();
        cart.setUser_id(userId);
        cart.setProduct_id(productId);
        cart.setQuantity(quantity);
        save(cart);
        return toCartItemVO(cart, product);
    }

    @Override
    public List<CartItemVO> listCart(Long userId) {
        List<Cart> cartList = lambdaQuery()
                .eq(Cart::getUser_id, userId)
                .orderByDesc(Cart::getCreate_time)
                .list();
        if (cartList.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> productIds = cartList.stream().map(Cart::getProduct_id).collect(Collectors.toSet());
        Map<Long, Product> productMap = productService.listByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        return cartList.stream()
                .map(cart -> toCartItemVO(cart, productMap.get(cart.getProduct_id())))
                .collect(Collectors.toList());
    }

    @Override
    public CartItemVO updateCart(Long userId, Long cartId, Integer quantity) {
        if (cartId == null || quantity == null || quantity < 1) {
            throw new ServiceException(400, "参数错误");
        }

        Cart cart = lambdaQuery()
                .eq(Cart::getId, cartId)
                .eq(Cart::getUser_id, userId)
                .one();
        if (cart == null) {
            throw new ServiceException(404, "购物车项不存在");
        }

        Product product = productService.getById(cart.getProduct_id());
        if (product == null || !Objects.equals(product.getStatus(), 1)) {
            throw new ServiceException(404, "商品不存在或已下架");
        }
        if (quantity > product.getStock()) {
            throw new ServiceException(400, "库存不足");
        }

        cart.setQuantity(quantity);
        updateById(cart);
        return toCartItemVO(cart, product);
    }

    @Override
    public void deleteCart(Long userId, Long cartId) {
        boolean removed = lambdaUpdate()
                .eq(Cart::getId, cartId)
                .eq(Cart::getUser_id, userId)
                .remove();
        if (!removed) {
            throw new ServiceException(404, "购物车项不存在");
        }
    }

    @Override
    public void removeByUserAndProductIds(Long userId, List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return;
        }
        lambdaUpdate()
                .eq(Cart::getUser_id, userId)
                .in(Cart::getProduct_id, productIds)
                .remove();
    }

    private CartItemVO toCartItemVO(Cart cart, Product product) {
        if (product == null) {
            return CartItemVO.builder()
                    .id(cart.getId())
                    .productId(cart.getProduct_id())
                    .productName("")
                    .productPrice(null)
                    .quantity(cart.getQuantity())
                    .imageUrl("")
                    .build();
        }
        return CartItemVO.builder()
                .id(cart.getId())
                .productId(product.getId())
                .productName(product.getName())
                .productPrice(product.getPrice())
                .quantity(cart.getQuantity())
                .imageUrl(product.getImage_url())
                .build();
    }
}




