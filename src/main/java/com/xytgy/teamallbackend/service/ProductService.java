package com.xytgy.teamallbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xytgy.teamallbackend.dto.MerchantGoodsAddRequest;
import com.xytgy.teamallbackend.dto.ProductAddRequest;
import com.xytgy.teamallbackend.dto.ProductAuditRequest;
import com.xytgy.teamallbackend.dto.ProductStatusRequest;
import com.xytgy.teamallbackend.dto.ProductUpdateRequest;
import com.xytgy.teamallbackend.entity.Product;
import com.xytgy.teamallbackend.vo.AuditVO;
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

    List<ProductVO> listMerchantProducts(Long merchantId);
    void addProduct(Long merchantId, ProductAddRequest request);
    void updateProduct(Long merchantId, ProductUpdateRequest request);
    void updateProductStatus(Long merchantId, ProductStatusRequest request);

    List<AuditVO> listPendingAuditProducts();
    void auditProduct(ProductAuditRequest request);
}
