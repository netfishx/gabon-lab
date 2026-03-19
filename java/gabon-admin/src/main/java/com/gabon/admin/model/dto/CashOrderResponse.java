package com.gabon.admin.model.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.gabon.admin.config.InstantToSecondsSerializer;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "资金订单响应")
public class CashOrderResponse {

    @Schema(description = "订单ID", example = "1")
    private Long id;

    @Schema(description = "订单号", example = "CW1773610000000")
    private String orderNo;

    @Schema(description = "客户ID", example = "12")
    private Long customerId;

    @Schema(description = "客户用户名", example = "zhangsan")
    private String customerUsername;

    @Schema(description = "客户昵称", example = "张三")
    private String customerName;

    @Schema(description = "订单类型: 1=提现, 2=充值", example = "1")
    private Integer orderType;

    @Schema(description = "订单状态", example = "2")
    private Integer status;

    @Schema(description = "审核管理员ID", example = "1")
    private Long reviewedByAdminId;

    @Schema(description = "审核管理员用户名", example = "adminA")
    private String reviewedByAdminUsername;

    @Schema(description = "金额", example = "20.00")
    private BigDecimal amount;

    @Schema(description = "币种", example = "CNY")
    private String currency;

    @Schema(description = "第三方订单号", example = "TP316143025")
    private String thirdPartyOrderNo;

    @Schema(description = "审核时间")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant reviewedTime;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "完成时间")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant completedTime;

    @Schema(description = "创建时间")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant createTime;
}
