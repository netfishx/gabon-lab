package com.gabon.service.model.vo;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.gabon.service.config.InstantToSecondsSerializer;
import com.gabon.service.model.entity.CustomerCashOrder;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "客户资金订单")
public class CustomerCashOrderVO {

    @Schema(description = "订单ID", example = "1")
    private Long id;

    @Schema(description = "订单号", example = "CW202603160001")
    private String orderNo;

    @Schema(description = "订单类型: 1=提现, 2=充值", example = "1")
    private Integer orderType;

    @Schema(description = "订单状态", example = "2")
    private Integer status;

    @Schema(description = "法币金额", example = "20.00")
    private BigDecimal fiatAmount;

    @Schema(description = "钻石数量", example = "2000")
    private Long diamondAmount;

    @Schema(description = "币种", example = "CNY")
    private String currencyCode;

    @Schema(description = "汇率（每1 CNY对应钻石数）", example = "100")
    private BigDecimal exchangeRate;

    @Schema(description = "失败原因")
    private String failureReason;

    @Schema(type = "integer", description = "审核时间（时间戳秒）", example = "1773611111")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant reviewedTime;

    @Schema(type = "integer", description = "完成时间（时间戳秒）", example = "1773612222")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant completedTime;

    @Schema(type = "integer", description = "创建时间（时间戳秒）", example = "1773610000")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant createTime;

    public static CustomerCashOrderVO fromEntity(CustomerCashOrder order) {
        return CustomerCashOrderVO.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .orderType(order.getOrderType())
                .status(order.getStatus())
                .fiatAmount(order.getFiatAmount())
                .diamondAmount(order.getDiamondAmount())
                .currencyCode(order.getCurrencyCode())
                .exchangeRate(order.getExchangeRate())
                .failureReason(order.getFailureReason())
                .reviewedTime(order.getReviewedTime())
                .completedTime(order.getCompletedTime())
                .createTime(order.getCreateTime())
                .build();
    }
}
