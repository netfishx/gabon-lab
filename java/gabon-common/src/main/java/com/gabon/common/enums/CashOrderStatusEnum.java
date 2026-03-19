package com.gabon.common.enums;

import lombok.Getter;

/**
 * 资金订单状态
 */
@Getter
public enum CashOrderStatusEnum {

    PENDING_ADMIN_REVIEW(1, "待审核"),
    REJECTED(2, "已拒绝"),
    PROCESSING(3, "处理中"),
    SUCCESS(4, "成功"),
    FAILED(5, "失败");

    private final int code;
    private final String description;

    CashOrderStatusEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == REJECTED;
    }

    public static CashOrderStatusEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (CashOrderStatusEnum value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }
}
