package com.xytgy.teamallbackend.common;

import java.util.Map;

public class UserContext {
    private static final ThreadLocal<Map<String, Object>> userThreadLocal = new ThreadLocal<>();

    //ThreadLocal存当前的请求的用户信息，实现全局可访问 + 线程隔离
    public static void setUser(Map<String, Object> user) {
        userThreadLocal.set(user);
    }

    public static Map<String, Object> getUser() {
        return userThreadLocal.get();
    }

    public static Long getCurrentUserId() {
        Map<String, Object> user = getUser();
        if (user != null && user.get("id") != null) {
            return Long.valueOf(user.get("id").toString());
        }
        return null;
    }

    public static Long getShopId() {
        Map<String, Object> user = getUser();
        if (user != null && user.get("shopId") != null) {
            return Long.valueOf(user.get("shopId").toString());
        }
        return null;
    }

    public static void setShopId(Long shopId) {
        Map<String, Object> user = getUser();
        if (user != null && shopId != null) {
            user.put("shopId", shopId);
            setUser(user);
        }
    }

    public static void clear() {
        userThreadLocal.remove();
    }
}
