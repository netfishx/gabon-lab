package com.gabon.common.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 账户角色枚举
 * User Role Enum
 * 
 * For gabon-admin module:
 * - ADMIN (code 1): 管理员，拥有完全权限
 * - NORMAL (code 2): 普通用户，受限权限
 */
@Getter
@AllArgsConstructor
@Schema(description = "账户角色枚举 | User Role Enum", enumAsRef = true)
public enum UserRole {
    @Schema(description = "1-管理员 (Admin)")
    ADMIN(1, "管理员", "Admin"),

    @Schema(description = "2-普通用户 (Normal User)")
    NORMAL(2, "普通用户", "Normal User");

    private final Integer code;
    private final String message;
    private final String englishName;

    public static UserRole getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (UserRole role : values()) {
            if (role.getCode().equals(code)) {
                return role;
            }
        }
        return null;
    }

    /**
     * 检查是否是管理员
     */
    public boolean isAdmin() {
        return this == ADMIN;
    }

    /**
     * 检查是否是普通用户
     */
    public boolean isNormal() {
        return this == NORMAL;
    }
}
