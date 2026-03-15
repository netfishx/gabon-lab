package com.gabon.common.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 商家类型枚举
 * Business Type Enum
 */
@Getter
@AllArgsConstructor
@Schema(description = "商家类型枚举 | Business Type Enum", enumAsRef = true)
public enum BusinessType {
    @Schema(description = "1-供应商 (Provider)")
    PROVIDER(1, "供应商", "Provider"),
    
    @Schema(description = "2-商家 (Business)")
    BUSINESS(2, "商家", "Business");

    private final Integer code;
    private final String message;
    private final String englishName;

    public static BusinessType getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (BusinessType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }
}

