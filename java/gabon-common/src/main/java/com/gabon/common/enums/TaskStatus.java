package com.gabon.common.enums;

import lombok.Getter;

/**
 * Task Status Enum
 */
@Getter
public enum TaskStatus {

    IN_PROGRESS(1, "进行中", "In Progress"),
    COMPLETED(2, "已完成", "Completed"),
    CLAIMED(3, "已领取", "Claimed"),
    EXPIRED(4, "已过期", "Expired");

    private final Integer code;
    private final String nameCn;
    private final String nameEn;

    TaskStatus(Integer code, String nameCn, String nameEn) {
        this.code = code;
        this.nameCn = nameCn;
        this.nameEn = nameEn;
    }

    /**
     * Get TaskStatus by code
     */
    public static TaskStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (TaskStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * Check if task is claimable (completed but not claimed)
     */
    public boolean isClaimable() {
        return this == COMPLETED;
    }

    /**
     * Check if task is active (in progress or completed)
     */
    public boolean isActive() {
        return this == IN_PROGRESS || this == COMPLETED;
    }
}
