package com.xytgy.teamallbackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.entity.Product;
import com.xytgy.teamallbackend.dto.MerchantGoodsAddRequest;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.service.ProductService;
import com.xytgy.teamallbackend.mapper.ProductMapper;
import com.xytgy.teamallbackend.vo.ProductVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;
/**
* @author xytgy
* @description 针对表【product】的数据库操作Service实现
* @createDate 2026-04-14 20:05:50
*/
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product>
    implements ProductService{

    @Override
    public List<ProductVO> listAvailableProducts() {
        return lambdaQuery()
                .eq(Product::getStatus, 1)
                .gt(Product::getStock, 0)
                .orderByDesc(Product::getUpdate_time)
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
        product.setMerchant_id(merchantId);
        // 数据库无 sales 字段时依赖表默认值；有字段时建议 default 0
        save(product);
        return product.getId();
    }

    private ProductVO toVO(Product product) {
        return ProductVO.builder()
                .id(product.getId())
                .name(product.getName())
                .price(product.getPrice())
                .stock(product.getStock())
                .imageUrl(product.getImage_url())
                .build();
    }
}




