package com.gabon.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 存储提供商枚举
 * Storage Provider Enum
 */
@Getter
@AllArgsConstructor
public enum StorageProvider {
    LOCAL(1, "本地", "Local"),
    S3(2, "亚马逊S3", "Amazon S3"),
    CDN(3, "CDN", "CDN"),
    OTHER(4, "其他", "Other");

    private final Integer code;
    private final String message;
    private final String englishName;

    public static StorageProvider getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (StorageProvider provider : values()) {
            if (provider.getCode().equals(code)) {
                return provider;
            }
        }
        return null;
    }
}

