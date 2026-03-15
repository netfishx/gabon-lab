package com.gabon.admin.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gabon.common.model.BaseDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * 客户交易记录实体
 * Customer Transaction Entity
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("customer_transactions")
@Schema(description = "客户交易记录实体")
public class CustomerTransaction extends BaseDO {

    @Schema(description = "客户ID", example = "1")
    private Long customerId;

    @Schema(description = "交易类型: 0=提现, 1=充值, 2=观看奖励, 3=任务奖励, 4=签到奖励, 5=邀请奖励", example = "1")
    private Integer transactionType;

    @Schema(description = "金额(钻石数量)", example = "1000")
    private Long amount;

    @Schema(description = "状态: 1=待处理, 2=成功, 3=失败", example = "2")
    private Integer status;

    @Schema(description = "支付方式", example = "alipay")
    private String paymentMethod;

    @Schema(description = "交易流水号", example = "TXN202602091234567890")
    private String transactionNo;

    @Schema(description = "备注", example = "充值1000钻石")
    private String remark;

    @Schema(description = "交易时间")
    private Instant transactionTime;
}
