package com.xytgy.teamallbackend.common;

import java.util.Map;

public class UserContext {
    private static final ThreadLocal<CurrentUser> userThreadLocal = new ThreadLocal<>();

    //ThreadLocal存当前的请求的用户信息，实现全局可访问 + 线程隔离
    public static void setUser(CurrentUser user) {
        userThreadLocal.set(user);
    }

    public static CurrentUser getUser() {
        return userThreadLocal.get();
    }

    public static Long getCurrentUserId() {
        CurrentUser user = getUser();
        if (user != null) {
            return user.getId();
        }
        return null;
    }

    public static Long getShopId() {
        CurrentUser user = getUser();
        if (user != null) {
            return user.getShopId();
        }
        return null;
    }

    public static void setShopId(Long shopId) {
        CurrentUser user = getUser();
        if (user != null && shopId != null) {
            user.setShopId(shopId);
            setUser(user);
        }
    }

    public static Integer getRole() {
        CurrentUser user = getUser();
        if (user != null) {
            return user.getRole();
        }
        return null;
    }

    public static void clear() {
        userThreadLocal.remove();
    }
}
