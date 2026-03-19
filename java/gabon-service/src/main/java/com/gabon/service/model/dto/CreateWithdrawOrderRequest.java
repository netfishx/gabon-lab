package com.gabon.service.model.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "创建提现订单请求")
public class CreateWithdrawOrderRequest {

    @NotNull(message = "提现金额不能为空")
    @DecimalMin(value = "0.01", message = "提现金额必须大于0")
    @Schema(description = "提现金额（CNY）", example = "20.00")
    private BigDecimal fiatAmount;

    @NotBlank(message = "取款密码不能为空")
    @Schema(description = "取款密码", example = "abc123")
    private String withdrawalPassword;
}
