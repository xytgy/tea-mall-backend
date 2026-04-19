package com.xytgy.teamallbackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.dto.MerchantGoodsAddRequest;
import com.xytgy.teamallbackend.dto.ProductAddRequest;
import com.xytgy.teamallbackend.dto.ProductAuditRequest;
import com.xytgy.teamallbackend.dto.ProductStatusRequest;
import com.xytgy.teamallbackend.dto.ProductUpdateRequest;
import com.xytgy.teamallbackend.entity.Product;
import com.xytgy.teamallbackend.entity.User;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.mapper.ProductMapper;
import com.xytgy.teamallbackend.service.ProductService;
import com.xytgy.teamallbackend.service.UserService;
import com.xytgy.teamallbackend.vo.AuditVO;
import com.xytgy.teamallbackend.vo.ProductVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
            throw new ServiceException(400, "参数不完整");
        }
        if (request.getPrice().signum() < 0) {
            throw new ServiceException(400, "价格不能小于0");
        }
        if (request.getStock() < 0) {
            throw new ServiceException(400, "库存不能小于0");
        }
        if (request.getStatus() != 0 && request.getStatus() != 1) {
            throw new ServiceException(400, "status 仅支持 0 或 1");
        }

        Product product = new Product();
        product.setName(request.getName().trim());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
        product.setStatus(request.getStatus());
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
            throw new ServiceException(400, "参数不完整");
        }
        Product product = new Product();
        product.setName(request.getName().trim());
        product.setCategory(request.getCategory());
        product.setImageUrl(request.getImageUrl());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
        product.setStatus(request.getStatus());
        product.setMerchantId(merchantId);
        product.setAuditStatus(0); // 待审核
        product.setSales(0);
        save(product);
    }

    @Override
    public void updateProduct(Long merchantId, ProductUpdateRequest request) {
        if (request == null || request.getId() == null) {
            throw new ServiceException(400, "参数不完整");
        }
        Product product = getById(request.getId());
        if (product == null || !product.getMerchantId().equals(merchantId)) {
            throw new ServiceException(403, "无权修改该商品或商品不存在");
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
            throw new ServiceException(400, "参数不完整");
        }
        Product product = getById(request.getId());
        if (product == null || !product.getMerchantId().equals(merchantId)) {
            throw new ServiceException(403, "无权修改该商品或商品不存在");
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
        Map<Long, User> userMap = userService.listByIds(merchantIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return list.stream().map(p -> {
            User u = userMap.get(p.getMerchantId());
            return AuditVO.builder()
                    .id(p.getId())
                    .name(p.getName())
                    .merchant(u != null ? u.getNickname() : "未知商家")
                    .price(p.getPrice())
                    .submitTime(p.getCreateTime())
                    .status(p.getAuditStatus())
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    public void auditProduct(ProductAuditRequest request) {
        if (request == null || request.getId() == null || request.getStatus() == null) {
            throw new ServiceException(400, "参数不完整");
        }
        Product product = getById(request.getId());
        if (product == null) {
            throw new ServiceException(404, "商品不存在");
        }
        product.setAuditStatus(request.getStatus());
        updateById(product);
    }

    private ProductVO toVO(Product product) {
        return ProductVO.builder()
                .id(product.getId())
                .name(product.getName())
                .category(product.getCategory())
                .description(product.getDescription())
                .price(product.getPrice())
                .stock(product.getStock())
                .imageUrl(product.getImageUrl())
                .status(product.getStatus())
                .sales(product.getSales() == null ? 0 : product.getSales())
                .build();
    }
}




