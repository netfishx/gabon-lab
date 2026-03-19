package com.gabon.admin.model.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.gabon.admin.config.InstantToSecondsSerializer;
import com.gabon.admin.model.entity.CustomerCashOrder;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "待审核提现订单响应")
public class PendingWithdrawOrderResponse {

    @Schema(description = "订单ID", example = "101")
    private Long id;

    @Schema(description = "订单号", example = "C1779344158421037056")
    private String orderNo;

    @Schema(type = "integer", description = "申请时间（时间戳秒）", example = "1773454212")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant timestamp;

    @Schema(description = "客户ID", example = "12")
    private Long customerId;

    @Schema(description = "客户账号", example = "lisi@example.com")
    private String customerAccount;

    @Schema(description = "金额", example = "2250.00")
    private BigDecimal amount;

    @Schema(description = "币种", example = "CNY")
    private String currency;

    public static PendingWithdrawOrderResponse fromEntity(CustomerCashOrder order) {
        return PendingWithdrawOrderResponse.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .timestamp(order.getCreateTime())
                .customerId(order.getCustomerId())
                .customerAccount(order.getCustomerUsername())
                .amount(order.getFiatAmount())
                .currency(order.getCurrencyCode())
                .build();
    }
}
