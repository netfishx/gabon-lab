package com.gabon.admin.model.entity;

import java.math.BigDecimal;
import java.time.Instant;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gabon.common.model.BaseDO;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 客户资金订单
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("customer_cash_orders")
public class CustomerCashOrder extends BaseDO {

    private String orderNo;

    private Long customerId;

    private String customerUsername;

    private String customerName;

    private Integer orderType;

    private Integer status;

    private BigDecimal fiatAmount;

    private Long diamondAmount;

    private String currencyCode;

    private BigDecimal exchangeRate;

    private String paymentChannel;

    private String providerName;

    private String providerOrderNo;

    private String providerStatus;

    private Long reviewedByAdminId;

    private Instant reviewedTime;

    private String failureReason;

    private Instant completedTime;

    @TableField("external_reference")
    private String externalReference;
}
