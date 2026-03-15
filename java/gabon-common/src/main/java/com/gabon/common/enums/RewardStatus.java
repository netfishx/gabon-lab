package com.gabon.common.enums;

import lombok.Getter;

/**
 * Reward Status Enum
 */
@Getter
public enum RewardStatus {

    NOT_CLAIMABLE(0, "不可领取", "Not Claimable"),
    CLAIMABLE(1, "可领取", "Claimable"),
    CLAIMED(2, "已领取", "Claimed");

    private final Integer code;
    private final String nameCn;
    private final String nameEn;

    RewardStatus(Integer code, String nameCn, String nameEn) {
        this.code = code;
        this.nameCn = nameCn;
        this.nameEn = nameEn;
    }

    /**
     * Get RewardStatus by code
     */
    public static RewardStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (RewardStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
