package com.gabon.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 营收报表响应DTO
 * Revenue Report Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "营收报表响应")
public class RevenueReportResponse {

    @Schema(description = "日期(yyyy-MM-dd)", example = "2026-02-07")
    private String date;

    @Schema(description = "充值人数", example = "8")
    private Long rechargeUserCount;

    @Schema(description = "总收入(钻石)", example = "1300")
    private Long totalIncome;

    @Schema(description = "总支出(钻石)", example = "50")
    private Long totalExpense;

    @Schema(description = "总金额(钻石)", example = "1250")
    private Long totalProfit;
}
