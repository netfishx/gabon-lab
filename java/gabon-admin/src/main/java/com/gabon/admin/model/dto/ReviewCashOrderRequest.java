package com.gabon.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "资金订单审核请求")
public class ReviewCashOrderRequest {

    @NotNull(message = "审核状态不能为空")
    @Schema(description = "提现审核结果: 1=同意, 2=拒绝", example = "1")
    private Integer status;

    @Schema(description = "审核备注/拒绝原因", example = "账户存在风险")
    private String remark;
}
