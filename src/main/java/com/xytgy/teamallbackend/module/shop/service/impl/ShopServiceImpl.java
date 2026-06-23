package com.xytgy.teamallbackend.module.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.common.PageResult;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.shop.dto.ShopRegisterRequest;
import com.xytgy.teamallbackend.module.shop.dto.ShopUpdateRequest;
import com.xytgy.teamallbackend.module.shop.entity.Shop;
import com.xytgy.teamallbackend.module.shop.mapper.ShopMapper;
import com.xytgy.teamallbackend.module.shop.service.ShopService;
import com.xytgy.teamallbackend.module.shop.vo.ShopVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements ShopService {

    @Override
    public Long getShopIdByUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        LambdaQueryWrapper<Shop> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Shop::getUserId, userId);
        Shop shop = this.getOne(queryWrapper);
        return shop != null ? shop.getId() : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void registerShop(ShopRegisterRequest request, Long userId) {
        if (userId == null) {
            throw new ServiceException(ResultCode.UNAUTHORIZED, "用户未登录");
        }

        // 检查是否已经存在店铺
        LambdaQueryWrapper<Shop> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Shop::getUserId, userId);
        if (this.count(queryWrapper) > 0) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "该用户已经开通过店铺");
        }

        Shop shop = new Shop();
        shop.setUserId(userId);
        shop.setShopName(request.getShopName());
        shop.setBusinessLicense(request.getBusinessLicense());
        
        this.save(shop);
    }

    @Override
    public ShopVO getMyShopInfo(Long userId) {
        if (userId == null) {
            throw new ServiceException(ResultCode.UNAUTHORIZED, "用户未登录");
        }

        LambdaQueryWrapper<Shop> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Shop::getUserId, userId);
        Shop shop = this.getOne(queryWrapper);

        if (shop == null) {
            return null; // 或者抛出异常，视业务需要
        }

        ShopVO vo = new ShopVO();
        vo.setId(shop.getId());
        vo.setUserId(shop.getUserId());
        vo.setName(shop.getShopName());
        vo.setBusinessLicense(shop.getBusinessLicense());
        vo.setCreateTime(shop.getCreateTime());
        vo.setUpdateTime(shop.getUpdateTime());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateShop(ShopUpdateRequest request, Long userId) {
        if (userId == null) {
            throw new ServiceException(ResultCode.UNAUTHORIZED, "用户未登录");
        }

        LambdaQueryWrapper<Shop> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Shop::getUserId, userId);
        Shop shop = this.getOne(queryWrapper);

        if (shop == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "店铺不存在");
        }

        shop.setShopName(request.getShopName());
        shop.setBusinessLicense(request.getBusinessLicense());
        
        this.updateById(shop);
    }

    @Override
    public ShopVO getShopById(Long shopId) {
        if (shopId == null) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "店铺ID不能为空");
        }
        Shop shop = this.getById(shopId);
        if (shop == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "店铺不存在");
        }
        ShopVO vo = new ShopVO();
        vo.setId(shop.getId());
        vo.setUserId(shop.getUserId());
        vo.setName(shop.getShopName());
        vo.setBusinessLicense(shop.getBusinessLicense());
        vo.setCreateTime(shop.getCreateTime());
        vo.setUpdateTime(shop.getUpdateTime());
        return vo;
    }

    @Override
    public PageResult<ShopVO> listShops(int page, int pageSize, String keyword) {
        LambdaQueryWrapper<Shop> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Shop::getIsDeleted, 0);
        if (keyword != null && !keyword.isBlank()) {
            queryWrapper.like(Shop::getShopName, keyword);
        }
        queryWrapper.orderByDesc(Shop::getCreateTime);

        Page<Shop> pageResult = this.page(new Page<>(page, pageSize), queryWrapper);

        java.util.List<ShopVO> voList = pageResult.getRecords().stream().map(shop -> {
            ShopVO vo = new ShopVO();
            vo.setId(shop.getId());
            vo.setUserId(shop.getUserId());
            vo.setName(shop.getShopName());
            vo.setBusinessLicense(shop.getBusinessLicense());
            vo.setCreateTime(shop.getCreateTime());
            vo.setUpdateTime(shop.getUpdateTime());
            return vo;
        }).toList();

        return new PageResult<>(voList, pageResult.getTotal(), page, pageSize);
    }
}

