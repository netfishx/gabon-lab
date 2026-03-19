package com.gabon.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "资金订单完成请求")
public class CompleteCashOrderRequest {

    @NotNull(message = "完成状态不能为空")
    @Schema(description = "完成状态: 4=成功, 5=失败", example = "4")
    private Integer status;
}
