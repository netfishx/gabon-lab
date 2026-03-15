package com.gabon.common.enums;

import lombok.Getter;

/**
 * 关注状态枚举
 * Follow Status Enum
 */
@Getter
public enum FollowStatus {

    NOT_FOLLOWING(0, "未关注", "Not Following"),
    FOLLOWING(1, "已关注", "Following"),
    MUTUAL_FOLLOWING(2, "相互关注", "Mutual Following");

    private final Integer code;
    private final String nameCn;
    private final String nameEn;

    FollowStatus(Integer code, String nameCn, String nameEn) {
        this.code = code;
        this.nameCn = nameCn;
        this.nameEn = nameEn;
    }

    /**
     * Get FollowStatus by code
     */
    public static FollowStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (FollowStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * Check if status is following (已关注 or 相互关注)
     */
    public boolean isFollowing() {
        return this == FOLLOWING || this == MUTUAL_FOLLOWING;
    }

    /**
     * Check if status is mutual following (相互关注)
     */
    public boolean isMutualFollowing() {
        return this == MUTUAL_FOLLOWING;
    }
}
