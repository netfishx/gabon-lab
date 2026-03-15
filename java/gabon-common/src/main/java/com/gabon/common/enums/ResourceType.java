package com.gabon.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 资源类型枚举
 * Resource Type Enum
 */
@Getter
@AllArgsConstructor
public enum ResourceType {
    IMAGE(1, "图片", "Image"),
    VIDEO(2, "视频", "Video");

    private final Integer code;
    private final String message;
    private final String englishName;

    public static ResourceType getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (ResourceType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }
}

