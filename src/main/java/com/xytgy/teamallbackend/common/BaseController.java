package com.xytgy.teamallbackend.common;

import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.security.SecurityUtils;

/**
 * Controller 公共基类，提供当前登录用户信息的便捷获取方法。
 * <p>
 * 角色校验已迁移至 @PreAuthorize 注解，不再提供 requireRole() 方法。
 */
public abstract class BaseController {

    /** 获取当前登录用户 ID，未登录时抛出 401 */
    protected Long currentUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new ServiceException(ResultCode.UNAUTHORIZED, "未登录");
        }
        return userId;
    }

    /** 获取当前商家店铺 ID，非商家或未完善店铺信息时抛出 403 */
    protected Long currentShopId() {
        Long shopId = SecurityUtils.getCurrentShopId();
        if (shopId == null) {
            throw new ServiceException(ResultCode.FORBIDDEN, "请先完善店铺信息");
        }
        return shopId;
    }
}
