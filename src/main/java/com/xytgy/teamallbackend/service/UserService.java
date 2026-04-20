package com.xytgy.teamallbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xytgy.teamallbackend.dto.AdminUserAddRequest;
import com.xytgy.teamallbackend.entity.User;
import com.xytgy.teamallbackend.vo.LoginResponse;
import com.xytgy.teamallbackend.vo.UserVO;

import java.util.List;

/**
* @author xytgy
* @description 针对表【user】的数据库操作Service
* @createDate 2026-04-15 07:59:22
*/
public interface UserService extends IService<User> {
    LoginResponse login(String userAccount, String password);
    void register(String userAccount, String password, String confirmPassword, String phone);
    Long addUserByAdmin(AdminUserAddRequest request);
    List<UserVO> listUsersByAdmin();
    void updateUserStatusByAdmin(Long id, Integer status);
    boolean isUserEnabled(Long id);
}
