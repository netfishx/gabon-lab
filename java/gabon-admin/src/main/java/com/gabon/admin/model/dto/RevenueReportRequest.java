package com.gabon.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 营收报表请求DTO
 * Revenue Report Request DTO
 */
@Data
@Schema(description = "营收报表请求")
public class RevenueReportRequest {

    @NotNull(message = "开始日期不能为空")
    @Schema(description = "开始日期", example = "2026-02-01", required = true)
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    @Schema(description = "结束日期", example = "2026-02-09", required = true)
    private LocalDate endDate;

    @Schema(description = "页码", example = "1")
    private Integer page = 1;

    @Schema(description = "每页大小", example = "20")
    private Integer size = 20;
}
