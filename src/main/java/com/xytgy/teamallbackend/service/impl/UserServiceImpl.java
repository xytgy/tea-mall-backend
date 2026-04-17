package com.xytgy.teamallbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.vo.LoginResponse;
import com.xytgy.teamallbackend.entity.User;
import com.xytgy.teamallbackend.service.UserService;
import com.xytgy.teamallbackend.mapper.UserMapper;
import com.xytgy.teamallbackend.utils.JwtUtils;
import com.xytgy.teamallbackend.utils.PasswordUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
* @author xytgy
* @description 针对表【user】的数据库操作Service实现
* @createDate 2026-04-15 07:59:22
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{

    @Autowired
    private JwtUtils jwtUtils;

    @Override
    public LoginResponse login(String username, String password) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        User user = this.getOne(queryWrapper);

        if (user == null) {
            throw new RuntimeException("账号或密码错误");
        }

        String dbPassword = user.getUserPassword();
        boolean passwordMatched = PasswordUtil.match(password, dbPassword);
        // 兼容历史明文密码数据，登录成功后自动升级为加密存储
        if (!passwordMatched && password.equals(dbPassword)) {
            passwordMatched = true;
            user.setUserPassword(PasswordUtil.encrypt(password));
            this.updateById(user);
        }

        if (!passwordMatched) {
            throw new RuntimeException("账号或密码错误");
        }

        // 生成 JWT Token
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", user.getId());
        claims.put("username", user.getUsername());
        String roleStr = Integer.valueOf(1).equals(user.getUser_role()) ? "admin" : "user";
        claims.put("role", roleStr);
        String token = jwtUtils.createToken(claims);

        // 封装返回数据
        return LoginResponse.builder()
                .token(token)
                .userInfo(LoginResponse.UserInfo.builder()
                        .username(user.getUsername())
                        .role(roleStr)
                        .build())
                .build();
    }

    @Override
    public void register(String username, String password, String phone) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        if (this.count(queryWrapper) > 0) {
            throw new RuntimeException("该用户名已被注册");
        }

        User user = new User();
        user.setUsername(username);
        user.setUserAccount(username);
        user.setUserPassword(PasswordUtil.encrypt(password));
        user.setPhone(phone != null ? phone : "");
        user.setUser_role(0); // 默认普通用户
        user.setUserstatus(1); // 默认状态正常
        user.setIsDelete(0);

        this.save(user);
    }
}



