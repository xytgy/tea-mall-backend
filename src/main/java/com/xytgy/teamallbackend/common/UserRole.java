package com.xytgy.teamallbackend.common;

public enum UserRole {
    USER(0, "user"),
    ADMIN(1, "admin"),
    MERCHANT(2, "merchant");

    private final int code;
    private final String roleName;

    UserRole(int code, String roleName) {
        this.code = code;
        this.roleName = roleName;
    }

    public int getCode() {
        return code;
    }

    public String getRoleName() {
        return roleName;
    }

    public static UserRole fromCode(Integer code) {
        if (code == null) {
            return USER;
        }
        for (UserRole role : values()) {
            if (role.code == code) {
                return role;
            }
        }
        return USER;
    }
}
