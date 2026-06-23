package com.xytgy.teamallbackend.module.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.xytgy.teamallbackend.cache.bloom.event.ProductCreatedEvent;
import com.xytgy.teamallbackend.common.PageResult;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.common.mapstruct.CopyMapper;
import com.xytgy.teamallbackend.config.datasource.ReadOnly;
import com.xytgy.teamallbackend.module.product.dto.MerchantGoodsAddRequest;
import com.xytgy.teamallbackend.module.product.dto.ProductAddRequest;
import com.xytgy.teamallbackend.module.product.dto.ProductAuditRequest;
import com.xytgy.teamallbackend.module.product.dto.ProductStatusRequest;
import com.xytgy.teamallbackend.module.product.dto.ProductUpdateRequest;
import com.xytgy.teamallbackend.module.product.entity.Product;
import com.xytgy.teamallbackend.module.product.entity.ProductReview;
import com.xytgy.teamallbackend.module.product.mapper.ProductReviewMapper;
import com.xytgy.teamallbackend.module.user.entity.User;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.product.mapper.ProductMapper;
import com.xytgy.teamallbackend.module.product.service.ProductService;
import com.xytgy.teamallbackend.module.user.service.UserService;
import com.xytgy.teamallbackend.cache.facade.RedisUtils;
import com.xytgy.teamallbackend.module.product.vo.AuditVO;
import com.xytgy.teamallbackend.module.product.vo.ProductVO;
import com.xytgy.teamallbackend.module.product.vo.ProductReviewVO;
import com.xytgy.teamallbackend.module.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
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
@RequiredArgsConstructor
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product>
    implements ProductService{

    private final UserService userService;
    
    private final ShopService shopService;

    private final CopyMapper copyMapper;
    
    private final ProductReviewMapper productReviewMapper;

    private final RedisUtils redisUtils;

    private final ApplicationEventPublisher eventPublisher;

    private static final String CACHE_PRODUCT_DETAIL_PREFIX = "cache:product:detail:";
    private static final String CACHE_PRODUCT_REVIEWS_PREFIX = "cache:product:reviews:";
    private static final String CACHE_PRODUCT_LIST_VERSION_KEY = "cache:product:list:version";

    /**
     * 获取包含当前版本号的商品列表缓存前缀。
     * 版本号变更后，旧版本 Key 自动失效并等待 TTL 回收。
     */
    private String currentListCachePrefix() {
        return redisUtils.versionedKey(
                "cache:product:list:", CACHE_PRODUCT_LIST_VERSION_KEY, "");
    }

    /**
     * 使商品列表缓存失效：仅递增版本号（O(1)），旧缓存自然过期。
     * 替代原先的 deleteByPattern SCAN 操作，避免大面积缓存失效导致的 DB 压力飙升。
     */
    private void invalidateProductListCaches() {
        redisUtils.invalidateVersion(CACHE_PRODUCT_LIST_VERSION_KEY);
    }

    /**
     * 使单个商品的详情缓存和评论缓存失效（精确删除）。
     */
    private void invalidateProductCaches(Long productId) {
        redisUtils.delete(CACHE_PRODUCT_DETAIL_PREFIX + productId);
        redisUtils.delete(CACHE_PRODUCT_REVIEWS_PREFIX + productId);
    }

    @ReadOnly
    @Override
    public PageResult<ProductVO> listAvailableProducts(int page, int pageSize) {
        String cacheKey = currentListCachePrefix() + page + ":" + pageSize;
        return redisUtils.getOrLoad(cacheKey, new TypeReference<>() {}, 5, () -> {
            Page<Product> pageResult = lambdaQuery()
                    .eq(Product::getStatus, 1)
                    .eq(Product::getAuditStatus, 1)
                    .gt(Product::getStock, 0)
                    .orderByDesc(Product::getUpdateTime)
                    .page(new Page<>(page, pageSize));

            List<ProductVO> voList = pageResult.getRecords().stream()
                    .map(this::toVO)
                    .toList();

            return new PageResult<>(voList, pageResult.getTotal(), page, pageSize);
        });
    }

    @ReadOnly
    @Override
    public ProductVO getProductDetail(Long productId) {
        if (productId == null) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "商品ID不能为空");
        }

        String cacheKey = CACHE_PRODUCT_DETAIL_PREFIX + productId;
        return redisUtils.getOrLoad(cacheKey, new TypeReference<>() {}, 10, () -> {
            Product product = getById(productId);
            if (product == null) {
                throw new ServiceException(ResultCode.NOT_FOUND, "商品不存在");
            }
            if (product.getStatus() != 1 || product.getAuditStatus() != 1) {
                throw new ServiceException(ResultCode.NOT_FOUND, "商品已下架或未审核");
            }
            return toVO(product);
        });
    }

    @Override
    @Transactional
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
        eventPublisher.publishEvent(new ProductCreatedEvent(product.getId()));
        invalidateProductListCaches();
        return product.getId();
    }

    @ReadOnly
    @Override
    public PageResult<ProductVO> listMerchantProducts(Long merchantId, int page, int pageSize) {
        Page<Product> pageResult = lambdaQuery()
                .eq(Product::getMerchantId, merchantId)
                .orderByDesc(Product::getUpdateTime)
                .page(new Page<>(page, pageSize));

        List<ProductVO> voList = pageResult.getRecords().stream().map(p -> {
            ProductVO vo = toVO(p);
            vo.setStatus(p.getStatus());
            vo.setSales(p.getSales() == null ? 0 : p.getSales());
            return vo;
        }).toList();

        return new PageResult<>(voList, pageResult.getTotal(), page, pageSize);
    }

    @Override
    @Transactional
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
        eventPublisher.publishEvent(new ProductCreatedEvent(product.getId()));
        invalidateProductListCaches();
    }

    @Override
    @Transactional
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
        invalidateProductCaches(request.getId());
        invalidateProductListCaches();
    }

    @Override
    @Transactional
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
        invalidateProductCaches(request.getId());
        invalidateProductListCaches();
    }

    @ReadOnly
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
        }).toList();
    }

    @Override
    @Transactional
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
        invalidateProductCaches(request.getId());
        invalidateProductListCaches();
    }

    @ReadOnly
    @Override
    public List<ProductReviewVO> listProductReviews(Long productId) {
        if (productId == null) {
            return Collections.emptyList();
        }

        String cacheKey = "cache:product:reviews:" + productId;
        return redisUtils.getOrLoad(cacheKey, new TypeReference<>() {}, 5, () -> {
            QueryWrapper<ProductReview> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("product_id", productId).orderByDesc("create_time").last("LIMIT 50");
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
            }).toList();
        });
    }

    private ProductVO toVO(Product product) {
        ProductVO vo = copyMapper.toProductVO(product);
        vo.setSales(product.getSales() == null ? 0 : product.getSales());
        return vo;
    }

    @ReadOnly
    @Override
    public PageResult<ProductVO> listProducts(int page, int pageSize, String keyword, String category, String sort) {
        String searchHash = md5Hex((keyword == null ? "" : keyword) + ":"
                + (category == null ? "" : category) + ":"
                + (sort == null ? "" : sort));
        String cacheKey = currentListCachePrefix() + page + ":" + pageSize + ":" + searchHash;

        return redisUtils.getOrLoad(cacheKey, new TypeReference<>() {}, 5, () -> {
            var query = lambdaQuery()
                    .eq(Product::getStatus, 1)
                    .eq(Product::getAuditStatus, 1)
                    .gt(Product::getStock, 0);

            if (StringUtils.hasText(keyword)) {
                query.like(Product::getName, keyword);
            }
            if (StringUtils.hasText(category)) {
                query.eq(Product::getCategory, category);
            }

            switch (sort) {
                case "sales" -> query.orderByDesc(Product::getSales);
                case "price_asc" -> query.orderByAsc(Product::getPrice);
                case "price_desc" -> query.orderByDesc(Product::getPrice);
                default -> query.orderByDesc(Product::getCreateTime);
            }

            Page<Product> pageResult = query.page(new Page<>(page, pageSize));

            List<ProductVO> voList = pageResult.getRecords().stream()
                    .map(this::toVO)
                    .toList();

            return new PageResult<>(voList, pageResult.getTotal(), page, pageSize);
        });
    }

    private static String md5Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            // MD5 is guaranteed to be available in every JDK
            throw new IllegalStateException(e);
        }
    }
}
