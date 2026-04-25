package com.xytgy.teamallbackend.module.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.user.dto.AddressAddRequest;
import com.xytgy.teamallbackend.module.user.dto.AddressUpdateRequest;
import com.xytgy.teamallbackend.module.user.entity.Address;
import com.xytgy.teamallbackend.module.user.repository.AddressMapper;
import com.xytgy.teamallbackend.module.user.service.AddressService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AddressServiceImpl extends ServiceImpl<AddressMapper, Address> implements AddressService {

    @Override
    public List<Address> listAddress(Long userId) {
        if (userId == null) throw new ServiceException(ResultCode.UNAUTHORIZED, "未登录");
        return this.lambdaQuery()
                .eq(Address::getUserId, userId)
                .orderByDesc(Address::getIsDefault)
                .orderByDesc(Address::getCreateTime)
                .list();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addAddress(Long userId, AddressAddRequest request) {
        if (userId == null) throw new ServiceException(ResultCode.UNAUTHORIZED, "未登录");
        
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            clearDefaultAddress(userId);
        }

        Address address = new Address();
        address.setUserId(userId);
        address.setReceiverName(request.getReceiverName());
        address.setReceiverPhone(request.getReceiverPhone());
        address.setReceiverAddress(request.getReceiverAddress());
        address.setIsDefault(request.getIsDefault() != null ? request.getIsDefault() : false);
        
        this.save(address);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAddress(Long userId, AddressUpdateRequest request) {
        if (userId == null) throw new ServiceException(ResultCode.UNAUTHORIZED, "未登录");
        
        Address address = this.getById(request.getId());
        if (address == null || !address.getUserId().equals(userId)) {
            throw new ServiceException(ResultCode.FORBIDDEN, "无权修改此地址");
        }

        if (Boolean.TRUE.equals(request.getIsDefault())) {
            clearDefaultAddress(userId);
        }

        if (request.getReceiverName() != null) address.setReceiverName(request.getReceiverName());
        if (request.getReceiverPhone() != null) address.setReceiverPhone(request.getReceiverPhone());
        if (request.getReceiverAddress() != null) address.setReceiverAddress(request.getReceiverAddress());
        if (request.getIsDefault() != null) address.setIsDefault(request.getIsDefault());

        this.updateById(address);
    }

    @Override
    public void deleteAddress(Long userId, Long addressId) {
        if (userId == null) throw new ServiceException(ResultCode.UNAUTHORIZED, "未登录");
        
        Address address = this.getById(addressId);
        if (address == null || !address.getUserId().equals(userId)) {
            throw new ServiceException(ResultCode.FORBIDDEN, "无权删除此地址");
        }
        
        this.removeById(addressId);
    }

    private void clearDefaultAddress(Long userId) {
        this.lambdaUpdate()
                .eq(Address::getUserId, userId)
                .set(Address::getIsDefault, false)
                .update();
    }
}
