package com.xytgy.teamallbackend.module.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.cache.bloom.event.UserCreatedEvent;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.common.UserRole;
import com.xytgy.teamallbackend.module.user.dto.AdminUserAddRequest;
import com.xytgy.teamallbackend.module.user.dto.UserProfileUpdateRequest;
import com.xytgy.teamallbackend.module.user.entity.User;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.user.mapper.UserMapper;
import com.xytgy.teamallbackend.module.user.service.UserService;
import com.xytgy.teamallbackend.utils.JwtUtils;
import com.xytgy.teamallbackend.utils.PasswordUtil;
import com.xytgy.teamallbackend.ratelimit.RateLimitService;
import com.xytgy.teamallbackend.module.user.vo.LoginResponse;
import com.xytgy.teamallbackend.module.user.vo.UserOverviewStatsVO;
import com.xytgy.teamallbackend.module.user.vo.UserVO;
import com.xytgy.teamallbackend.module.user.mapper.UserStatsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.xytgy.teamallbackend.module.user.dto.LoginRequest;
import com.xytgy.teamallbackend.common.mapstruct.CopyMapper;
import com.xytgy.teamallbackend.module.user.dto.RegisterRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.xytgy.teamallbackend.security.SecurityUtils;
import java.time.format.DateTimeFormatter;
import com.xytgy.teamallbackend.module.user.cache.UserInfoCache;
import com.xytgy.teamallbackend.utils.AliyunOSSUtils;
import com.xytgy.teamallbackend.cache.facade.RedisUtils;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
    private static final String USER_INFO_CACHE_PREFIX = "user:info:";
    /** 用户信息缓存 TTL：7 天（与 Refresh Token 一致） */
    private static final long USER_INFO_CACHE_TTL_MINUTES = 7L * 24 * 60;
    /**
     * 用户 Refresh Token 集合前缀，用于退出登录时批量撤销
     * key: login:user:refresh:{userId}, value: Set of refreshTokens
     */
    private static final String USER_REFRESH_TOKENS_PREFIX = "login:user:refresh:";
    private static final String COLUMN_USER_ACCOUNT = "useraccount";
    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

    private final JwtUtils jwtUtils;
    private final StringRedisTemplate stringRedisTemplate;
    private final CopyMapper copyMapper;
    private final ShopService shopService;
    private final AliyunOSSUtils aliyunOSSUtils;
    private final RateLimitService rateLimitService;
    private final RedisUtils redisUtils;

    private final UserStatsMapper userStatsMapper;

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public LoginResponse login(LoginRequest request, String clientIp) {
        if (request == null || !StringUtils.hasText(request.getUserAccount()) || !StringUtils.hasText(request.getPassword())) {
            throw new ServiceException(ResultCode.BAD_REQUEST,"账号密码不能为空");
        }
        
        // 账号维度速率限制检查，防止暴力破解和撞库攻击
        String identifier = request.getUserAccount();
        long accountRate = rateLimitService.checkAccountRate(identifier);
        if (!RateLimitService.isAllowed(accountRate)) {
            if (RateLimitService.isLocked(accountRate)) {
                throw new ServiceException(ResultCode.ACCOUNT_LOCKED, "账号已被临时锁定，请稍后再试");
            }
            throw new ServiceException(ResultCode.TOO_MANY_REQUESTS, "登录尝试过于频繁，请稍后再试");
        }
        
        String userAccount = request.getUserAccount();
        String password = request.getPassword();

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(COLUMN_USER_ACCOUNT, userAccount);
        User user = this.getOne(queryWrapper);
        if (user == null) {
            throw new ServiceException(ResultCode.UNAUTHORIZED,"账号或密码错误");
        }
        if (!isUserEnabled(user.getId())) {
            throw new ServiceException(ResultCode.FORBIDDEN,"");
        }

        // 仅使用 BCrypt 验证密码，移除明文回退逻辑防止密码绕过攻击
        if (!PasswordUtil.match(password, user.getPassword())) {
            throw new ServiceException(ResultCode.UNAUTHORIZED, "账号或密码错误");
        }
        
        // 登录成功，重置该账号的失败计数器，防止正常用户被误锁定
        rateLimitService.resetAccountAttempts(identifier);
        
        return createLoginResponse(user);
    }

    @Override
    public boolean existsByAccount(String userAccount) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(COLUMN_USER_ACCOUNT, userAccount);
        return this.count(queryWrapper) > 0;
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

        // 校验通过，作废旧 Token 及其在用户 Token 集合中的引用
        stringRedisTemplate.delete(key);
        stringRedisTemplate.opsForSet().remove(USER_REFRESH_TOKENS_PREFIX + userIdStr, refreshToken);

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

        // 维护用户 -> RefreshToken 的反向映射，用于退出登录时批量撤销
        String userTokensKey = USER_REFRESH_TOKENS_PREFIX + user.getId();
        stringRedisTemplate.opsForSet().add(userTokensKey, refreshToken);
        stringRedisTemplate.expire(userTokensKey, 7, TimeUnit.DAYS);

        // 存入 Redis (在线状态)
        stringRedisTemplate.opsForValue().set(
                LOGIN_USER_KEY_PREFIX + user.getId(),
                "online",
                7, TimeUnit.DAYS
        );

        // 写入用户信息缓存（供 JwtAuthenticationFilter 快速校验，避免每次查 DB）
        cacheUserInfo(user.getId(), new UserInfoCache(
                user.getStatus() == null || user.getStatus() != 0,
                frontendRole, shopId));

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
    @Transactional
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
        queryWrapper.eq(COLUMN_USER_ACCOUNT, userAccount);
        if (this.count(queryWrapper) > 0) {
            // 使用模糊提示，防止攻击者通过注册接口枚举已存在用户
            throw new ServiceException(ResultCode.CONFLICT, "注册失败，请检查账号信息或尝试其他账号");
        }

        user.setPassword(PasswordUtil.encrypt(user.getPassword()));
        user.setPhone(user.getPhone() != null ? user.getPhone() : "");
        user.setRole(UserRole.USER.getCode()); // 默认普通用户
        user.setStatus(1); // 默认状态正常

        this.save(user);
        eventPublisher.publishEvent(new UserCreatedEvent(user.getId()));
        cacheUserStatus(user.getId(), user.getStatus());
    }

    @Override
    @Transactional
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
        existsQuery.eq(COLUMN_USER_ACCOUNT, userAccount);
        if (this.count(existsQuery) > 0) {
            throw new ServiceException(ResultCode.CONFLICT, "用户名已存在");
        }

        // 生成随机 12 位临时密码，替代硬编码的 "123456"
        String tempPassword = generateTempPassword();

        User user = copyMapper.toUser(request);
        user.setPassword(PasswordUtil.encrypt(tempPassword));
        user.setRole(toDbRole(request.getRole()));
        this.save(user);
        eventPublisher.publishEvent(new UserCreatedEvent(user.getId()));
        cacheUserStatus(user.getId(), user.getStatus());
        return user.getId();
    }

    /**
     * 生成包含大小写字母和数字的 12 位随机临时密码
     */
    private String generateTempPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
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
                .orderByDesc(User::getCreateTime)
                .list()
                .stream()
                .map(user -> {
                    UserVO vo = copyMapper.toUserVO(user);
                    vo.setRole(toFrontendRole(user.getRole()));
                    vo.setCreateTime(user.getCreateTime() == null ? null : user.getCreateTime().format(formatter));
                    return vo;
                })
                .toList();
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
        if (user == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "用户不存在");
        }
        user.setStatus(status);
        updateById(user);
        cacheUserStatus(user.getId(), user.getStatus());
        // 同步更新用户信息缓存中的 enabled 状态
        UserInfoCache cached = redisUtils.get(USER_INFO_CACHE_PREFIX + id, UserInfoCache.class);
        if (cached != null) {
            cached.setEnabled(status != 0);
            cacheUserInfo(id, cached);
        }
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
        if (user == null) {
            cacheUserStatus(id, 0);
            return false;
        }
        cacheUserStatus(id, user.getStatus());
        return user.getStatus() == null || user.getStatus() != 0;
    }

    @Override
    public void logout() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return;
        }
        // 清除在线状态
        stringRedisTemplate.delete(LOGIN_USER_KEY_PREFIX + userId);
        // 清除用户信息缓存
        redisUtils.delete(USER_INFO_CACHE_PREFIX + userId);
        // 撤销该用户的所有 RefreshToken，防止退出后仍可刷新
        revokeAllRefreshTokens(userId);
    }

    /**
     * 撤销指定用户的所有 RefreshToken（退出登录、密码修改等场景复用）
     */
    private void revokeAllRefreshTokens(Long userId) {
        String userTokensKey = USER_REFRESH_TOKENS_PREFIX + userId;
        java.util.Set<String> tokens = stringRedisTemplate.opsForSet().members(userTokensKey);
        if (tokens != null && !tokens.isEmpty()) {
            for (String token : tokens) {
                stringRedisTemplate.delete(REFRESH_TOKEN_KEY_PREFIX + token);
            }
        }
        stringRedisTemplate.delete(userTokensKey);
    }

    @Override
    public UserInfoVO getUserInfo(Long id) {
        if (id == null) {
            throw new ServiceException(ResultCode.UNAUTHORIZED, "未登录");
        }
        User user = this.getById(id);
        if (user == null || !isUserEnabled(id)) {
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
                // 使用 UUID 避免文件名可预测，防止用户头像被枚举遍历
                String fileName = UUID.randomUUID().toString().replace("-", "") + extension;
                
                // 调用 OSS 工具类上传文件，并获取可访问的 URL
                String avatarUrl = aliyunOSSUtils.uploadAvatar(inputStream, fileName);
                
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
            stringRedisTemplate.opsForValue().set(
                    userStatusKey(userId),
                    String.valueOf(status == null ? 1 : status),
                    30, java.util.concurrent.TimeUnit.MINUTES);
        } catch (Exception ignored) {
            // Redis 故障时不影响主流程
        }
    }

    /**
     * 写入用户信息缓存（L1 Caffeine + L2 Redis），自动处理穿透/雪崩/击穿
     */
    private void cacheUserInfo(Long userId, UserInfoCache info) {
        try {
            redisUtils.set(USER_INFO_CACHE_PREFIX + userId, info, USER_INFO_CACHE_TTL_MINUTES);
        } catch (Exception e) {
            // Redis 故障不影响主流程，过滤器会降级查 DB
        }
    }

    @Override
    public UserOverviewStatsVO getUserOverviewStats(Long userId) {
        long favoritesCount = userStatsMapper.countFavorites(userId);
        long ordersCount = userStatsMapper.countOrders(userId);
        long consultsCount = userStatsMapper.countSupportTickets(userId);

        return UserOverviewStatsVO.builder()
                .favorites((int) favoritesCount)
                .orders((int) ordersCount)
                .consults((int) consultsCount)
                .build();
    }
}
