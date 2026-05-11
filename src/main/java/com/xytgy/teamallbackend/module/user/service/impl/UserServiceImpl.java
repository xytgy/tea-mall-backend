package com.xytgy.teamallbackend.module.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.common.UserRole;
import com.xytgy.teamallbackend.module.user.dto.AdminUserAddRequest;
import com.xytgy.teamallbackend.module.user.dto.UserProfileUpdateRequest;
import com.xytgy.teamallbackend.module.user.entity.User;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.user.repository.UserMapper;
import com.xytgy.teamallbackend.module.user.service.UserService;
import com.xytgy.teamallbackend.utils.JwtUtils;
import com.xytgy.teamallbackend.utils.PasswordUtil;
import com.xytgy.teamallbackend.module.user.vo.LoginResponse;
import com.xytgy.teamallbackend.module.user.vo.UserOverviewStatsVO;
import com.xytgy.teamallbackend.module.user.vo.UserVO;
import com.xytgy.teamallbackend.module.favorite.entity.Favorite;
import com.xytgy.teamallbackend.module.order.entity.Orders;
import com.xytgy.teamallbackend.module.support.entity.SupportTicket;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.xytgy.teamallbackend.module.user.dto.LoginRequest;
import com.xytgy.teamallbackend.common.mapstruct.CopyMapper;
import com.xytgy.teamallbackend.module.user.dto.RegisterRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.xytgy.teamallbackend.common.UserContext;
import java.time.format.DateTimeFormatter;
import com.xytgy.teamallbackend.utils.AliyunOssUtil;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.xytgy.teamallbackend.module.shop.service.ShopService;

import com.xytgy.teamallbackend.module.user.vo.UserInfoVO;

/**
* @author xytgy
* @description 针对表【user】的数据库操作Service实现
* @createDate 2026-04-15 07:59:22
*/

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{
    private static final String USER_STATUS_KEY_PREFIX = "user:status:";
    private static final String LOGIN_USER_KEY_PREFIX = "login:user:";
    private static final String REFRESH_TOKEN_KEY_PREFIX = "login:refresh:token:";

    private final JwtUtils jwtUtils;
    private final StringRedisTemplate stringRedisTemplate;
    private final CopyMapper copyMapper;
    private final ShopService shopService;
    private final AliyunOssUtil aliyunOssUtil;
    private final UserLazyDeps userLazyDeps;

    @Override
    public LoginResponse login(LoginRequest request) {
        if (request == null || !StringUtils.hasText(request.getUserAccount()) || !StringUtils.hasText(request.getPassword())) {
            throw new ServiceException(ResultCode.BAD_REQUEST,"账号密码不能为空");
        }
        String userAccount = request.getUserAccount();
        String password = request.getPassword();

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("useraccount", userAccount);
        User user = this.getOne(queryWrapper);
        if (user == null) {
            throw new ServiceException(ResultCode.UNAUTHORIZED,"账号或密码错误");
        }
        if (!isUserEnabled(user.getId())) {
            throw new ServiceException(ResultCode.FORBIDDEN,"");
        }

        String dbPassword = user.getPassword();
        Boolean passwordMatched = PasswordUtil.match(password, dbPassword);
        //明码兼容
        if (!passwordMatched && password.equals(dbPassword)) {
            passwordMatched = true;
            user.setPassword(PasswordUtil.encrypt(password));
            this.updateById(user);
        }
        if (!passwordMatched) {
            throw new ServiceException(ResultCode.UNAUTHORIZED, "账号或密码错误");
        }
        return createLoginResponse(user);
    }

    @Override
    public LoginResponse refreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "RefreshToken 不能为空");
        }

        String key = REFRESH_TOKEN_KEY_PREFIX + refreshToken;
        String userIdStr = stringRedisTemplate.opsForValue().get(key);

        if (!StringUtils.hasText(userIdStr)) {
            throw new ServiceException(ResultCode.UNAUTHORIZED, "RefreshToken 已过期或无效，请重新登录");
        }

        // 校验通过，作废旧 Token
        stringRedisTemplate.delete(key);

        // 生成新的一对 Token
        Long userId = Long.valueOf(userIdStr);
        User user = this.getById(userId);
        if (user == null || !isUserEnabled(userId)) {
            throw new ServiceException(ResultCode.UNAUTHORIZED, "账号状态异常，请重新登录");
        }

        return createLoginResponse(user);
    }

    private LoginResponse createLoginResponse(User user) {
        // 生成 JWT AccessToken
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", user.getId());
        claims.put("userAccount", user.getUserAccount());
        Integer frontendRole = toFrontendRole(user.getRole());
        claims.put("role", frontendRole);
        
        Long shopId = null;
        if (frontendRole == 1) { // 商家角色
            shopId = shopService.getShopIdByUserId(user.getId());
            if (shopId != null) {
                claims.put("shopId", shopId);
            }
        }

        String accessToken = jwtUtils.createAccessToken(claims);

        // 生成 RefreshToken (UUID)
        String refreshToken = UUID.randomUUID().toString().replace("-", "");
        
        // 存入 Redis (RefreshToken)
        stringRedisTemplate.opsForValue().set(
                REFRESH_TOKEN_KEY_PREFIX + refreshToken,
                user.getId().toString(),
                7, TimeUnit.DAYS // 默认 7 天，可以从配置读
        );

        // 存入 Redis (在线状态)
        stringRedisTemplate.opsForValue().set(
                LOGIN_USER_KEY_PREFIX + user.getId(),
                "online",
                7, TimeUnit.DAYS
        );

        // 封装返回数据
        LoginResponse.UserInfo userInfo = copyMapper.toUserInfo(user);
        userInfo.setRole(frontendRole);
        userInfo.setShopId(shopId);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userInfo(userInfo)
                .build();
    }

    @Override
    public void register(RegisterRequest request) {
        if (request == null || !StringUtils.hasText(request.getUserAccount()) || !StringUtils.hasText(request.getPassword()) || !StringUtils.hasText(request.getConfirmPassword())) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "账号和密码不能为空");
        }
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "两次输入的密码不一致");
        }
        
        User user = copyMapper.toUser(request);
        String userAccount = user.getUserAccount();
        
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("useraccount", userAccount);
        if (this.count(queryWrapper) > 0) {
            throw new ServiceException(ResultCode.CONFLICT, "该账号已被注册");
        }

        user.setPassword(PasswordUtil.encrypt(user.getPassword()));
        user.setPhone(user.getPhone() != null ? user.getPhone() : "");
        user.setRole(UserRole.USER.getCode()); // 默认普通用户
        user.setStatus(1); // 默认状态正常
        user.setIsDeleted(0);

        this.save(user);
        cacheUserStatus(user.getId(), user.getStatus());
    }

    @Override
    public Long addUserByAdmin(AdminUserAddRequest request) {
        if (request == null || !StringUtils.hasText(request.getUserAccount())
                || request.getRole() == null || request.getStatus() == null) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "参数不完整");
        }
        if (request.getStatus() != 0 && request.getStatus() != 1) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "status 仅支持 0 或 1");
        }
        if (request.getRole() < 0 || request.getRole() > 2) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "role 仅支持 0/1/2");
        }

        String userAccount = request.getUserAccount().trim();
        QueryWrapper<User> existsQuery = new QueryWrapper<>();
        existsQuery.eq("useraccount", userAccount);
        if (this.count(existsQuery) > 0) {
            throw new ServiceException(ResultCode.CONFLICT, "用户名已存在");
        }

        User user = copyMapper.toUser(request);
        user.setPassword(PasswordUtil.encrypt("123456"));
        user.setRole(toDbRole(request.getRole()));
        user.setIsDeleted(0);
        this.save(user);
        cacheUserStatus(user.getId(), user.getStatus());
        return user.getId();
    }

    /**
     * 前端传参角色约定：0普通买家 1商家 2管理员
     * 当前数据库角色约定：0用户 1商家 2管理员
     */
    private Integer toDbRole(Integer requestRole) {
        return switch (requestRole) {
            case 2 -> 2;
            case 1 -> 1;
            default -> 0;
        };
    }

    private Integer toFrontendRole(Integer dbRole) {
        return switch (dbRole) {
            case 2 -> 2; // DB Admin(2) -> Frontend Admin(2)
            case 1 -> 1; // DB Merchant(1) -> Frontend Merchant(1)
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
                .map(user -> {
                    UserVO vo = copyMapper.toUserVO(user);
                    vo.setRole(toFrontendRole(user.getRole()));
                    vo.setCreateTime(user.getCreateTime() == null ? null : user.getCreateTime().format(formatter));
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public void updateUserStatusByAdmin(Long id, Integer status) {
        if (id == null || status == null) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "参数不完整");
        }
        if (status != 0 && status != 1) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "状态值非法");
        }
        User user = getById(id);
        if (user == null || user.getIsDeleted() == 1) {
            throw new ServiceException(ResultCode.NOT_FOUND, "用户不存在");
        }
        user.setStatus(status);
        updateById(user);
        cacheUserStatus(user.getId(), user.getStatus());
    }

    @Override
    public boolean isUserEnabled(Long id) {
        if (id == null) {
            return false;
        }
        //设置成redis查询的形式
        String key = userStatusKey(id);
        try {
            String cachedStatus = stringRedisTemplate.opsForValue().get(key);
            if (cachedStatus != null) {
                return !"0".equals(cachedStatus);
            }
        } catch (Exception ignored) {
            // Redis 故障时降级到数据库
        }

        //数据库进行查找，要是没有找到或者是逻辑删除，redis状态设置为0，然后返回
        User user = getById(id);
        if (user == null || user.getIsDeleted() == 1) {
            cacheUserStatus(id, 0);
            return false;
        }
        cacheUserStatus(id, user.getStatus());
        return user.getStatus() == null || user.getStatus() != 0;
    }

    @Override
    public void logout() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return;
        }
        // 清除在线状态
        stringRedisTemplate.delete(LOGIN_USER_KEY_PREFIX + userId);
        
        // 注意：因为 RefreshToken 是 UUID 作为 key 存的，我们目前没有维护 userId -> refreshToken 的反向映射。
        // 由于只要删除了在线状态 (LOGIN_USER_KEY_PREFIX)，拦截器就会拦截所有请求，达到登出效果。
    }

    @Override
    public UserInfoVO getUserInfo(Long id) {
        if (id == null) {
            throw new ServiceException(ResultCode.UNAUTHORIZED, "未登录");
        }
        User user = this.getById(id);
        if (user == null || user.getIsDeleted() == 1 || !isUserEnabled(id)) {
            throw new ServiceException(ResultCode.UNAUTHORIZED, "账号状态异常或已被封禁，请重新登录");
        }

        Integer frontendRole = toFrontendRole(user.getRole());
        Long shopId = null;

        // 如果是商家，查询对应的 shopId
        if (frontendRole == 1) {
            shopId = shopService.getShopIdByUserId(user.getId());
        }

        return UserInfoVO.builder()
                .id(user.getId())
                .userAccount(user.getUserAccount())
                .nickname(user.getNickname())
                .bio("") // TODO: User entity doesn't have a bio field yet, return empty string for now
                .gender(user.getGender())
                .phone(user.getPhone())
                .avatar(user.getAvatar())
                .role(frontendRole)
                .shopId(shopId)
                .build();
    }

    @Override
    public String updateAvatar(Long userId, String avatarBase64) {
        if (userId == null) {
            throw new ServiceException(ResultCode.UNAUTHORIZED, "未登录");
        }
        User user = this.getById(userId);
        if (user == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "用户不存在");
        }
        
        if (!StringUtils.hasText(avatarBase64)) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "图片数据为空");
        }
        
        // 解析 Base64，移除前缀如 "data:image/png;base64,"
        String base64Data = avatarBase64;
        String extension = ".png"; // 默认扩展名
        
        if (avatarBase64.contains(",")) {
            String[] parts = avatarBase64.split(",");
            if (parts.length == 2) {
                // 尝试提取扩展名
                String header = parts[0];
                if (header.contains("image/jpeg") || header.contains("image/jpg")) {
                    extension = ".jpg";
                } else if (header.contains("image/gif")) {
                    extension = ".gif";
                } else if (header.contains("image/webp")) {
                    extension = ".webp";
                }
                base64Data = parts[1];
            }
        }
        
        try {
            // 解码 base64
            byte[] decodedBytes = Base64.getDecoder().decode(base64Data);
            
            // 大小限制 (如 2MB = 2 * 1024 * 1024 bytes)
            if (decodedBytes.length > 2 * 1024 * 1024) {
                throw new ServiceException(ResultCode.BAD_REQUEST, "图片过大，请上传小于2MB的图片");
            }
            
            // 使用 ByteArrayInputStream 包装字节数组
            try (InputStream inputStream = new ByteArrayInputStream(decodedBytes)) {
                // 生成临时文件名（带扩展名）用于 OSS 上传
                String fileName = "avatar_" + userId + extension;
                
                // 调用 OSS 工具类上传文件，并获取可访问的 URL
                String avatarUrl = aliyunOssUtil.upload(inputStream, fileName);
                
                // 更新数据库
                user.setAvatar(avatarUrl);
                this.updateById(user);
                
                return avatarUrl;
            }
            
        } catch (IllegalArgumentException e) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "图片格式错误，无法解析");
        } catch (Exception e) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "头像上传失败: " + e.getMessage());
        }
    }

    @Override
    public void updateProfile(Long userId, UserProfileUpdateRequest request) {
        if (userId == null) {
            throw new ServiceException(ResultCode.UNAUTHORIZED, "未登录");
        }
        User user = this.getById(userId);
        if (user == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "用户不存在");
        }
        
        if (request.getNickname() != null) user.setNickname(request.getNickname());
        if (request.getGender() != null) user.setGender(request.getGender());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        // User 表目前没有 bio 字段，如果有需要可以后续在 DB 中加字段。暂不处理 bio。

        this.updateById(user);
    }

    private String userStatusKey(Long userId) {
        return USER_STATUS_KEY_PREFIX + userId;
    }

    private void cacheUserStatus(Long userId, Integer status) {
        if (userId == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(userStatusKey(userId), String.valueOf(status == null ? 1 : status));
        } catch (Exception ignored) {
            // Redis 故障时不影响主流程
        }
    }

    @Override
    public UserOverviewStatsVO getUserOverviewStats(Long userId) {
        long favoritesCount = userLazyDeps.getFavoriteService().lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .count();

        long ordersCount = userLazyDeps.getOrdersService().lambdaQuery()
                .eq(Orders::getUserId, userId)
                .ne(Orders::getStatus, 4) // 这里排除了“已取消(4)”状态的订单，按需调整
                .count();

        long consultsCount = userLazyDeps.getSupportService().lambdaQuery()
                .eq(SupportTicket::getUserId, userId)
                .count();

        return UserOverviewStatsVO.builder()
                .favorites((int) favoritesCount)
                .orders((int) ordersCount)
                .consults((int) consultsCount)
                .build();
    }
}
