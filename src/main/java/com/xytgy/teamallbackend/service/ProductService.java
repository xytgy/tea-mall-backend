package com.xytgy.teamallbackend.service;

import com.xytgy.teamallbackend.entity.Product;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xytgy.teamallbackend.dto.MerchantGoodsAddRequest;
import com.xytgy.teamallbackend.vo.ProductVO;

import java.util.List;

/**
* @author xytgy
* @description 针对表【product】的数据库操作Service
* @createDate 2026-04-14 20:05:50
*/
public interface ProductService extends IService<Product> {
    List<ProductVO> listAvailableProducts();
    Long addMerchantGoods(Long merchantId, MerchantGoodsAddRequest request);
}
