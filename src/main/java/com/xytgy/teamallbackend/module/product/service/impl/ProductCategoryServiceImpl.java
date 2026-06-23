package com.xytgy.teamallbackend.module.product.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.module.product.entity.ProductCategory;
import com.xytgy.teamallbackend.module.product.mapper.ProductCategoryMapper;
import com.xytgy.teamallbackend.module.product.service.ProductCategoryService;
import com.xytgy.teamallbackend.module.product.vo.CategoryVO;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
* @author xytgy
* @description 针对表【product_category】的数据库操作Service实现
*/
@Service
public class ProductCategoryServiceImpl extends ServiceImpl<ProductCategoryMapper, ProductCategory>
    implements ProductCategoryService {

    @Override
    public List<CategoryVO> listActiveCategories() {
        List<ProductCategory> categories = lambdaQuery()
                .eq(ProductCategory::getStatus, 1)
                .orderByDesc(ProductCategory::getSortOrder)
                .list();

        if (categories.isEmpty()) {
            return Collections.emptyList();
        }

        return categories.stream()
                .map(c -> CategoryVO.builder()
                        .id(c.getId())
                        .name(c.getName())
                        .icon(c.getIcon())
                        .build())
                .toList();
    }
}
