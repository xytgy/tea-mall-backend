package com.xytgy.teamallbackend.module.user.cache;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户信息缓存 DTO，存储在 Redis 中，供 JwtAuthenticationFilter 快速校验。
 * 避免每次请求都查 MySQL，同时内置防穿透/雪崩/击穿能力（通过 RedisUtils）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoCache {

    /** 用户是否启用：true=正常，false=已禁用 */
    private boolean enabled;

    /** 用户角色：0=普通用户，1=商家，2=管理员 */
    private Integer role;

    /** 商家店铺 ID，非商家用户为 null */
    private Long shopId;
}
