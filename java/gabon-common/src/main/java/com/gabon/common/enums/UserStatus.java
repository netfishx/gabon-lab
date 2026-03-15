package com.gabon.common.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 账户状态枚举
 * User Status Enum
 */
@Getter
@AllArgsConstructor
@Schema(description = "账户状态枚举 | User Status Enum", enumAsRef = true)
public enum UserStatus {
    @Schema(description = "0-禁用 (Disabled)")
    DISABLED(0, "禁用"),
    
    @Schema(description = "1-启用 (Enabled)")
    ENABLED(1, "启用");

    private final Integer code;
    private final String message;

    public static UserStatus getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (UserStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }
}
