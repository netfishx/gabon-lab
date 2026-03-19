package com.gabon.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 广告日报查询请求DTO
 */
@Data
@Schema(description = "广告日报查询请求")
public class AdReportRequest {

    @NotNull(message = "开始日期不能为空")
    @Schema(description = "开始日期", example = "2026-03-01", required = true)
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    @Schema(description = "结束日期", example = "2026-03-16", required = true)
    private LocalDate endDate;

    @Schema(description = "广告商ID，不传则查全部", example = "1")
    private Long advertiserId;

    @Schema(description = "页码", example = "1")
    private Integer page = 1;

    @Schema(description = "每页大小", example = "20")
    private Integer size = 20;

}
