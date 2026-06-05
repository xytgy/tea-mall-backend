package com.xytgy.teamallbackend.module.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.user.dto.AddressAddRequest;
import com.xytgy.teamallbackend.module.user.dto.AddressUpdateRequest;
import com.xytgy.teamallbackend.module.user.entity.Address;
import com.xytgy.teamallbackend.module.user.mapper.AddressMapper;
import com.xytgy.teamallbackend.module.user.service.AddressService;
import com.xytgy.teamallbackend.common.mapstruct.CopyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressServiceImpl extends ServiceImpl<AddressMapper, Address> implements AddressService {

    private final CopyMapper copyMapper;

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

        Address address = copyMapper.toAddress(request);
        address.setUserId(userId);
        if (address.getIsDefault() == null) {
            address.setIsDefault(false);
        }
        
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

        copyMapper.updateAddress(address, request);

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
