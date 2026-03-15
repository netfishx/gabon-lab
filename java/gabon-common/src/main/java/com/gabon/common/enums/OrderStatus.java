package com.gabon.common.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单状态枚举
 * Order Status Enum
 */
@Getter
@AllArgsConstructor
@Schema(description = "订单状态枚举 | Order Status Enum", enumAsRef = true)
public enum OrderStatus {
    @Schema(description = "1-成功 (Success)")
    SUCCESS(1, "成功", "Success"),
    
    @Schema(description = "2-失败 (Failed)")
    FAILED(2, "失败", "Failed");

    private final Integer code;
    private final String message;
    private final String englishName;

    public static OrderStatus getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (OrderStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 检查订单是否成功
     */
    public boolean isSuccess() {
        return this == SUCCESS;
    }

    /**
     * 检查订单是否失败
     */
    public boolean isFailed() {
        return this == FAILED;
    }
}

