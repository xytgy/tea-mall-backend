package com.xytgy.teamallbackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.entity.Product;
import com.xytgy.teamallbackend.service.ProductService;
import com.xytgy.teamallbackend.mapper.ProductMapper;
import org.springframework.stereotype.Service;

/**
* @author xytgy
* @description 针对表【product】的数据库操作Service实现
* @createDate 2026-04-14 20:05:50
*/
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product>
    implements ProductService{

}




