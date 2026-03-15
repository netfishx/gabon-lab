package com.gabon.common.enums;

import lombok.Getter;

/**
 * 客户交易类型枚举
 */
@Getter
public enum TransactionTypeEnum {

    WITHDRAW(0, "取现"),
    RECHARGE(1, "充値"),
    WATCH_REWARD(2, "观看奖励"),
    TASK_REWARD(3, "任务奖励"),
    SIGN_IN_REWARD(4, "签到奖励"),
    INVITE_REWARD(5, "邀请奖励");

    private final int code;
    private final String description;

    TransactionTypeEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static TransactionTypeEnum getByCode(int code) {
        for (TransactionTypeEnum type : values()) {
            if (type.getCode() == code) {
                return type;
            }
        }
        return null;
    }
}
