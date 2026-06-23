package com.xytgy.teamallbackend.module.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xytgy.teamallbackend.module.product.entity.ProductCategory;
import com.xytgy.teamallbackend.module.product.vo.CategoryVO;

import java.util.List;

/**
* @author xytgy
* @description 针对表【product_category】的数据库操作Service
*/
public interface ProductCategoryService extends IService<ProductCategory> {

    List<CategoryVO> listActiveCategories();
}
