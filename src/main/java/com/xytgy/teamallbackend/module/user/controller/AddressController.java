package com.xytgy.teamallbackend.module.user.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.common.UserContext;
import com.xytgy.teamallbackend.module.user.dto.AddressAddRequest;
import com.xytgy.teamallbackend.module.user.dto.AddressUpdateRequest;
import com.xytgy.teamallbackend.module.user.entity.Address;
import com.xytgy.teamallbackend.module.user.service.AddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "收货地址接口")
@RequestMapping("/api/user/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping("/list")
    @Operation(summary = "获取收货地址列表")
    public Result<List<Address>> list() {
        Long userId = UserContext.getCurrentUserId();
        return Result.success("获取成功", addressService.listAddress(userId));
    }

    @PostMapping("/add")
    @Operation(summary = "添加收货地址")
    public Result<Void> add(@RequestBody AddressAddRequest request) {
        Long userId = UserContext.getCurrentUserId();
        addressService.addAddress(userId, request);
        return Result.success("添加成功", null);
    }

    @PostMapping("/update")
    @Operation(summary = "更新收货地址")
    public Result<Void> update(@RequestBody AddressUpdateRequest request) {
        Long userId = UserContext.getCurrentUserId();
        addressService.updateAddress(userId, request);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除收货地址")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        addressService.deleteAddress(userId, id);
        return Result.success("删除成功", null);
    }
}
