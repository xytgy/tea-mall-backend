package com.xytgy.teamallbackend.module.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xytgy.teamallbackend.module.user.dto.AddressAddRequest;
import com.xytgy.teamallbackend.module.user.dto.AddressUpdateRequest;
import com.xytgy.teamallbackend.module.user.entity.Address;
import java.util.List;

public interface AddressService extends IService<Address> {
    List<Address> listAddress(Long userId);
    void addAddress(Long userId, AddressAddRequest request);
    void updateAddress(Long userId, AddressUpdateRequest request);
    void deleteAddress(Long userId, Long addressId);
}
