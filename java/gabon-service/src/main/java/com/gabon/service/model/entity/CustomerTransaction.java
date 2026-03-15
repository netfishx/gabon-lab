package com.gabon.service.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gabon.common.model.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * 客户交易记录表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("customer_transactions")
public class CustomerTransaction extends BaseDO {

    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 交易类型: 0=提现, 1=充值, 2=观看奖励, 3=任务奖励, 4=签到奖励, 5=邀请奖励
     */
    private Integer transactionType;

    /**
     * 金额(钻石数量)
     */
    private Long amount;

    /**
     * 状态: 1=待处理, 2=成功, 3=失败
     */
    private Integer status;

    /**
     * 支付方式
     */
    private String paymentMethod;

    /**
     * 交易流水号
     */
    private String transactionNo;

    /**
     * 备注
     */
    private String remark;

    /**
     * 交易时间
     */
    private Instant transactionTime;
}
