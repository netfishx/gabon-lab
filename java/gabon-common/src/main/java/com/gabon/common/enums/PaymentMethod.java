package com.gabon.common.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 支付方式枚举
 * Payment Method Enum
 */
@Getter
@AllArgsConstructor
@Schema(description = "支付方式枚举 | Payment Method Enum", enumAsRef = true)
public enum PaymentMethod {
    @Schema(description = "1-价格 (Price)")
    PRICE(1, "价格", "Price"),
    
    @Schema(description = "2-积分 (Point)")
    POINT(2, "积分", "Point");

    private final Integer code;
    private final String message;
    private final String englishName;

    public static PaymentMethod getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (PaymentMethod method : values()) {
            if (method.getCode().equals(code)) {
                return method;
            }
        }
        return null;
    }
}

