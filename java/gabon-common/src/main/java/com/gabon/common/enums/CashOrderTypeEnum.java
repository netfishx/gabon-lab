package com.gabon.common.enums;

import lombok.Getter;

/**
 * 资金订单类型
 */
@Getter
public enum CashOrderTypeEnum {

    WITHDRAW(1, "提现"),
    RECHARGE(2, "充值");

    private final int code;
    private final String description;

    CashOrderTypeEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static CashOrderTypeEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (CashOrderTypeEnum value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }
}
