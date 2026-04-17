package com.xytgy.teamallbackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.entity.Product;
import com.xytgy.teamallbackend.service.ProductService;
import com.xytgy.teamallbackend.mapper.ProductMapper;
import com.xytgy.teamallbackend.vo.ProductVO;
import org.springframework.stereotype.Service;

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




