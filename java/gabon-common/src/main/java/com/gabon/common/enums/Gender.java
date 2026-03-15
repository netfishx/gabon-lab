package com.gabon.common.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 性别枚举
 * Gender Enum
 */
@Getter
@AllArgsConstructor
@Schema(description = "性别枚举 | Gender Enum", enumAsRef = true)
public enum Gender {
    @Schema(description = "1-男 (Male)")
    MALE(1, "男", "Male"),
    
    @Schema(description = "2-女 (Female)")
    FEMALE(2, "女", "Female");

    private final Integer code;
    private final String message;
    private final String englishName;

    public static Gender getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (Gender gender : values()) {
            if (gender.getCode().equals(code)) {
                return gender;
            }
        }
        return null;
    }
}

