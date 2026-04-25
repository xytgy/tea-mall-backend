package com.xytgy.teamallbackend.module.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xytgy.teamallbackend.module.user.dto.AdminUserAddRequest;
import com.xytgy.teamallbackend.module.user.dto.LoginRequest;
import com.xytgy.teamallbackend.module.user.dto.RegisterRequest;
import com.xytgy.teamallbackend.module.user.entity.User;
import com.xytgy.teamallbackend.module.user.vo.LoginResponse;
import com.xytgy.teamallbackend.module.user.vo.UserInfoVO;
import com.xytgy.teamallbackend.module.user.vo.UserVO;

import java.util.List;

/**
* @author xytgy
* @description 针对表【user】的数据库操作Service
* @createDate 2026-04-15 07:59:22
*/
public interface UserService extends IService<User> {
    LoginResponse login(LoginRequest request);
    LoginResponse refreshToken(String refreshToken);
    void register(RegisterRequest request);
    Long addUserByAdmin(AdminUserAddRequest request);
    List<UserVO> listUsersByAdmin();
    void updateUserStatusByAdmin(Long id, Integer status);
    boolean isUserEnabled(Long id);
    void logout();
    UserInfoVO getUserInfo(Long id);
}
