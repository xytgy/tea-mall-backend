package com.xytgy.teamallbackend.module.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.common.mapstruct.CopyMapper;
import com.xytgy.teamallbackend.module.product.dto.MerchantGoodsAddRequest;
import com.xytgy.teamallbackend.module.product.dto.ProductAddRequest;
import com.xytgy.teamallbackend.module.product.dto.ProductAuditRequest;
import com.xytgy.teamallbackend.module.product.dto.ProductStatusRequest;
import com.xytgy.teamallbackend.module.product.dto.ProductUpdateRequest;
import com.xytgy.teamallbackend.module.product.entity.Product;
import com.xytgy.teamallbackend.module.product.entity.ProductReview;
import com.xytgy.teamallbackend.module.product.repository.ProductReviewMapper;
import com.xytgy.teamallbackend.module.user.entity.User;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.product.repository.ProductMapper;
import com.xytgy.teamallbackend.module.product.service.ProductService;
import com.xytgy.teamallbackend.module.user.service.UserService;
import com.xytgy.teamallbackend.module.product.vo.AuditVO;
import com.xytgy.teamallbackend.module.product.vo.ProductVO;
import com.xytgy.teamallbackend.module.product.vo.ProductReviewVO;
import com.xytgy.teamallbackend.module.shop.service.ShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
* @author xytgy
* @description 针对表【product】的数据库操作Service实现
* @createDate 2026-04-14 20:05:50
*/
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product>
    implements ProductService{

    @Autowired
    private UserService userService;
    
    @Autowired
    private ShopService shopService;

    @Autowired
    private CopyMapper copyMapper;
    
    @Autowired
    private ProductReviewMapper productReviewMapper;

    @Override
    public List<ProductVO> listAvailableProducts() {
        return lambdaQuery()
                .eq(Product::getStatus, 1)
                .eq(Product::getAuditStatus, 1)
                .gt(Product::getStock, 0)
                .orderByDesc(Product::getUpdateTime)
                .list()
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public Long addMerchantGoods(Long merchantId, MerchantGoodsAddRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getName())
                || request.getPrice() == null
                || request.getStock() == null
                || request.getStatus() == null) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "参数不完整");
        }
        if (request.getPrice().signum() < 0) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "价格不能小于0");
        }
        if (request.getStock() < 0) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "库存不能小于0");
        }
        if (request.getStatus() != 0 && request.getStatus() != 1) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "status 仅支持 0 或 1");
        }

        Product product = copyMapper.toProduct(request);
        product.setName(request.getName().trim());
        product.setMerchantId(merchantId);
        // 数据库无 sales 字段时依赖表默认值；有字段时建议 default 0
        save(product);
        return product.getId();
    }

    @Override
    public List<ProductVO> listMerchantProducts(Long merchantId) {
        return lambdaQuery()
                .eq(Product::getMerchantId, merchantId)
                .orderByDesc(Product::getUpdateTime)
                .list()
                .stream()
                .map(p -> {
                    ProductVO vo = toVO(p);
                    vo.setStatus(p.getStatus());
                    vo.setSales(p.getSales() == null ? 0 : p.getSales());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public void addProduct(Long merchantId, ProductAddRequest request) {
        if (request == null || !StringUtils.hasText(request.getName())
                || request.getPrice() == null || request.getStock() == null
                || request.getStatus() == null) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "参数不完整");
        }
        Product product = copyMapper.toProduct(request);
        product.setName(request.getName().trim());
        product.setMerchantId(merchantId);
        product.setAuditStatus(0); // 待审核
        product.setSales(0);
        save(product);
    }

    @Override
    public void updateProduct(Long merchantId, ProductUpdateRequest request) {
        if (request == null || request.getId() == null) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "参数不完整");
        }
        Product product = getById(request.getId());
        if (product == null || !product.getMerchantId().equals(merchantId)) {
            throw new ServiceException(ResultCode.FORBIDDEN, "无权修改该商品或商品不存在");
        }
        if (StringUtils.hasText(request.getName())) {
            product.setName(request.getName().trim());
        }
        if (StringUtils.hasText(request.getCategory())) {
            product.setCategory(request.getCategory());
        }
        if (StringUtils.hasText(request.getImageUrl())) {
            product.setImageUrl(request.getImageUrl());
        }
        if (StringUtils.hasText(request.getDescription())) {
            product.setDescription(request.getDescription());
        }
        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }
        if (request.getStock() != null) {
            product.setStock(request.getStock());
        }
        if (request.getStatus() != null) {
            product.setStatus(request.getStatus());
        }
        product.setAuditStatus(0); // 重新审核
        updateById(product);
    }

    @Override
    public void updateProductStatus(Long merchantId, ProductStatusRequest request) {
        if (request == null || request.getId() == null || request.getStatus() == null) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "参数不完整");
        }
        Product product = getById(request.getId());
        if (product == null || !product.getMerchantId().equals(merchantId)) {
            throw new ServiceException(ResultCode.FORBIDDEN, "无权修改该商品或商品不存在");
        }
        product.setStatus(request.getStatus());
        updateById(product);
    }

    @Override
    public List<AuditVO> listPendingAuditProducts() {
        List<Product> list = lambdaQuery()
                .eq(Product::getAuditStatus, 0)
                .orderByAsc(Product::getCreateTime)
                .list();

        if (list.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> merchantIds = list.stream().map(Product::getMerchantId).collect(Collectors.toSet());
        // 注意：现在 merchantId 实际存储的是 shopId，所以先通过 shop 找到对应的 userId
        Map<Long, com.xytgy.teamallbackend.module.shop.entity.Shop> shopMap = shopService.listByIds(merchantIds).stream()
                .collect(Collectors.toMap(com.xytgy.teamallbackend.module.shop.entity.Shop::getId, s -> s));
                
        Set<Long> userIds = shopMap.values().stream().map(com.xytgy.teamallbackend.module.shop.entity.Shop::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return list.stream().map(p -> {
            com.xytgy.teamallbackend.module.shop.entity.Shop s = shopMap.get(p.getMerchantId());
            User u = s != null ? userMap.get(s.getUserId()) : null;
            AuditVO vo = copyMapper.toAuditVO(p, u);
            if (u == null) {
                vo.setMerchant("未知商家");
            } else {
                vo.setMerchant(s.getShopName()); // 也可以改为显示店铺名
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public void auditProduct(ProductAuditRequest request) {
        if (request == null || request.getId() == null || request.getStatus() == null) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "参数不完整");
        }
        Product product = getById(request.getId());
        if (product == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "商品不存在");
        }
        product.setAuditStatus(request.getStatus());
        updateById(product);
    }

    @Override
    public List<ProductReviewVO> listProductReviews(Long productId) {
        if (productId == null) {
            return Collections.emptyList();
        }
        
        QueryWrapper<ProductReview> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("product_id", productId).orderByDesc("create_time");
        List<ProductReview> reviews = productReviewMapper.selectList(queryWrapper);
        
        if (reviews == null || reviews.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> userIds = reviews.stream().map(ProductReview::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        return reviews.stream().map(r -> {
            User u = userMap.get(r.getUserId());
            ProductReviewVO vo = copyMapper.toProductReviewVO(r, u);
            if (u == null) {
                vo.setUsername("匿名用户");
            }
            vo.setCreateTime(r.getCreateTime() == null ? null : r.getCreateTime().format(formatter));
            return vo;
        }).collect(Collectors.toList());
    }

    private ProductVO toVO(Product product) {
        ProductVO vo = copyMapper.toProductVO(product);
        vo.setSales(product.getSales() == null ? 0 : product.getSales());
        return vo;
    }
}



