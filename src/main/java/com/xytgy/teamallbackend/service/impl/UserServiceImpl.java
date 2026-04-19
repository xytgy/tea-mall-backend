package com.xytgy.teamallbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.common.UserRole;
import com.xytgy.teamallbackend.dto.AdminUserAddRequest;
import com.xytgy.teamallbackend.entity.User;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.mapper.UserMapper;
import com.xytgy.teamallbackend.service.UserService;
import com.xytgy.teamallbackend.utils.JwtUtils;
import com.xytgy.teamallbackend.utils.PasswordUtil;
import com.xytgy.teamallbackend.vo.LoginResponse;
import com.xytgy.teamallbackend.vo.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    public LoginResponse login(String userAccount, String password) {
        if (!StringUtils.hasText(userAccount) || !StringUtils.hasText(password)) {
            throw new ServiceException(400, "账号和密码不能为空");
        }
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("useraccount", userAccount);
        User user = this.getOne(queryWrapper);

        if (user == null) {
            throw new ServiceException(401, "账号或密码错误");
        }

        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new ServiceException(403, "账号已被禁用，请联系管理员");
        }

        String dbPassword = user.getPassword();
        boolean passwordMatched = PasswordUtil.match(password, dbPassword);
        // 兼容历史明文密码数据，登录成功后自动升级为加密存储
        if (!passwordMatched && password.equals(dbPassword)) {
            passwordMatched = true;
            user.setPassword(PasswordUtil.encrypt(password));
            this.updateById(user);
        }

        if (!passwordMatched) {
            throw new ServiceException(401, "账号或密码错误");
        }

        // 生成 JWT Token
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", user.getId());
        claims.put("userAccount", user.getUserAccount());
        Integer frontendRole = toFrontendRole(user.getRole());
        claims.put("role", frontendRole);
        String token = jwtUtils.createToken(claims);

        // 封装返回数据
        return LoginResponse.builder()
                .token(token)
                .userInfo(LoginResponse.UserInfo.builder()
                        .username(user.getUserAccount())
                        .role(frontendRole)
                        .build())
                .build();
    }

    @Override
    public void register(String userAccount, String password, String confirmPassword, String phone) {
        if (!StringUtils.hasText(userAccount) || !StringUtils.hasText(password) || !StringUtils.hasText(confirmPassword)) {
            throw new ServiceException(400, "账号和密码不能为空");
        }
        if (!password.equals(confirmPassword)) {
            throw new ServiceException(400, "两次输入的密码不一致");
        }
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("useraccount", userAccount);
        if (this.count(queryWrapper) > 0) {
            throw new ServiceException(409, "该账号已被注册");
        }

        User user = new User();
        user.setUserAccount(userAccount);
        user.setPassword(PasswordUtil.encrypt(password));
        user.setPhone(phone != null ? phone : "");
        user.setRole(UserRole.USER.getCode()); // 默认普通用户
        user.setStatus(1); // 默认状态正常
        user.setIsDeleted(0);

        this.save(user);
    }

    @Override
    public Long addUserByAdmin(AdminUserAddRequest request) {
        if (request == null || !StringUtils.hasText(request.getUsername())
                || request.getRole() == null || request.getStatus() == null) {
            throw new ServiceException(400, "参数不完整");
        }
        if (request.getStatus() != 0 && request.getStatus() != 1) {
            throw new ServiceException(400, "status 仅支持 0 或 1");
        }
        if (request.getRole() < 0 || request.getRole() > 2) {
            throw new ServiceException(400, "role 仅支持 0/1/2");
        }

        String username = request.getUsername().trim();
        QueryWrapper<User> existsQuery = new QueryWrapper<>();
        existsQuery.eq("useraccount", username);
        if (this.count(existsQuery) > 0) {
            throw new ServiceException(400, "用户名已存在");
        }

        User user = new User();
        user.setUserAccount(username);
        user.setPassword(PasswordUtil.encrypt("123456"));
        user.setRole(toDbRole(request.getRole()));
        user.setStatus(request.getStatus());
        user.setIsDeleted(0);
        this.save(user);
        return user.getId();
    }

    /**
     * 前端传参角色约定：0用户 1管理员 2商家
     * 当前数据库角色约定：0用户 1商家 2管理员
     */
    private Integer toDbRole(Integer requestRole) {
        return switch (requestRole) {
            case 1 -> 2;
            case 2 -> 1;
            default -> 0;
        };
    }

    private Integer toFrontendRole(Integer dbRole) {
        return switch (dbRole) {
            case 2 -> 1; // DB Admin(2) -> Frontend Admin(1)
            case 1 -> 2; // DB Merchant(1) -> Frontend Merchant(2)
            default -> 0; // User(0)
        };
    }

    @Override
    public List<UserVO> listUsersByAdmin() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return lambdaQuery()
                .eq(User::getIsDeleted, 0)
                .orderByDesc(User::getCreateTime)
                .list()
                .stream()
                .map(user -> UserVO.builder()
                        .id(user.getId())
                        .username(user.getUserAccount())
                        .nickname(user.getNickname())
                        .avatar(user.getAvatar())
                        .gender(user.getGender())
                        .phone(user.getPhone())
                        .email(user.getEmail())
                        .status(user.getStatus())
                        .role(toFrontendRole(user.getRole()))
                        .createTime(user.getCreateTime() == null ? null : user.getCreateTime().format(formatter))
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public void updateUserStatusByAdmin(Long id, Integer status) {
        if (id == null || status == null) {
            throw new ServiceException(400, "参数不完整");
        }
        if (status != 0 && status != 1) {
            throw new ServiceException(400, "状态值非法");
        }
        User user = getById(id);
        if (user == null || user.getIsDeleted() == 1) {
            throw new ServiceException(404, "用户不存在");
        }
        user.setStatus(status);
        updateById(user);
    }
}
