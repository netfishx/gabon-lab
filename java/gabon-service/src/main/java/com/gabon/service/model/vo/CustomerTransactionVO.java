package com.gabon.service.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.gabon.service.config.InstantToSecondsSerializer;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;

/**
 * 客户交易记录返回对象
 */
@Data
@Schema(description = "客户交易记录信息")
public class CustomerTransactionVO {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "交易类型: 0=取现, 1=充値, 2=观看奖励, 3=任务奖励, 4=签到奖励, 5=邀请奖励")
    private Integer transactionType;

    @Schema(description = "金额(钻石数量)")
    private Long amount;

    @Schema(description = "状态: 1=待处理, 2=成功, 3=失败")
    private Integer status;

    @Schema(description = "支付方式")
    private String paymentMethod;

    @Schema(description = "交易流水号")
    private String transactionNo;

    @Schema(description = "备注")
    private String remark;

    @Schema(type = "integer", description = "交易时间（时间戳-秒）", example = "1739174903")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant transactionTime;
}
