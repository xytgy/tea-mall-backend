package com.xytgy.teamallbackend.common.mapstruct;

import com.xytgy.teamallbackend.module.user.dto.AdminUserAddRequest;
import com.xytgy.teamallbackend.module.user.dto.LoginRequest;
import com.xytgy.teamallbackend.module.user.dto.RegisterRequest;
import com.xytgy.teamallbackend.module.user.entity.User;
import com.xytgy.teamallbackend.module.user.vo.LoginResponse;
import com.xytgy.teamallbackend.module.user.vo.UserVO;
import com.xytgy.teamallbackend.module.product.dto.MerchantGoodsAddRequest;
import com.xytgy.teamallbackend.module.product.dto.ProductAddRequest;
import com.xytgy.teamallbackend.module.product.entity.Product;
import com.xytgy.teamallbackend.module.product.entity.ProductReview;
import com.xytgy.teamallbackend.module.product.vo.AuditVO;
import com.xytgy.teamallbackend.module.product.vo.ProductReviewVO;
import com.xytgy.teamallbackend.module.product.vo.ProductVO;
import com.xytgy.teamallbackend.module.cart.entity.Cart;
import com.xytgy.teamallbackend.module.cart.vo.CartItemVO;
import com.xytgy.teamallbackend.module.order.entity.OrderItem;
import com.xytgy.teamallbackend.module.order.entity.Orders;
import com.xytgy.teamallbackend.module.favorite.entity.Favorite;
import com.xytgy.teamallbackend.module.order.dto.OrderCreateRequest;
import com.xytgy.teamallbackend.module.order.vo.MerchantOrderVO;
import com.xytgy.teamallbackend.module.order.vo.OrderItemVO;
import com.xytgy.teamallbackend.module.order.vo.OrderVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

// componentModel = "spring" 表示生成的实现类会标注 @Component，方便 Spring 注入
@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE, // 忽略字段不匹配警告
        unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface CopyMapper {

    /**
     * User 模块
     */
    User toUser(RegisterRequest request);
    User toUser(LoginRequest request);
    User toUser(AdminUserAddRequest request);
    
    UserVO toUserVO(User user);
    
    LoginResponse.UserInfo toUserInfo(User user);

    /**
     * Product 模块
     */
    Product toProduct(MerchantGoodsAddRequest request);
    Product toProduct(ProductAddRequest request);
    ProductVO toProductVO(Product product);

    @Mapping(target = ".", source = "product")
    @Mapping(source = "product.id", target = "id")
    @Mapping(source = "user.nickname", target = "merchant")
    @Mapping(source = "product.createTime", target = "submitTime")
    @Mapping(source = "product.auditStatus", target = "status")
    AuditVO toAuditVO(Product product, User user);

    @Mapping(target = ".", source = "review")
    @Mapping(source = "review.id", target = "id")
    @Mapping(source = "user.userAccount", target = "username")
    @Mapping(source = "user.avatar", target = "avatar")
    @Mapping(source = "review.createTime", target = "createTime")
    ProductReviewVO toProductReviewVO(ProductReview review, User user);

    /**
     * Cart 模块
     */
    @Mapping(target = ".", source = "cart")
    @Mapping(target = ".", source = "product")
    @Mapping(source = "cart.id", target = "id")
    @Mapping(source = "product.id", target = "productId")
    CartItemVO toCartItemVO(Cart cart, Product product);

    /**
     * Order 模块
     */
    OrderItemVO toOrderItemVO(OrderItem item);
    
    List<OrderItemVO> toOrderItemVOList(List<OrderItem> items);

    @Mapping(target = ".", source = "order")
    @Mapping(source = "order.id", target = "id")
    @Mapping(source = "items", target = "items")
    OrderVO toOrderVO(Orders order, List<OrderItem> items);

    @Mapping(target = ".", source = "order")
    @Mapping(source = "order.id", target = "id")
    @Mapping(source = "items", target = "items")
    MerchantOrderVO toMerchantOrderVO(Orders order, List<OrderItem> items);

    Orders toOrders(OrderCreateRequest request);

    /**
     * Favorite 模块
     */
    @Mapping(source = "userId", target = "userId")
    @Mapping(source = "productId", target = "productId")
    Favorite toFavorite(Long userId, Long productId);
}
