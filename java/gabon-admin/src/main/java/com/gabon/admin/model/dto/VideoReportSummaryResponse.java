package com.gabon.admin.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.gabon.admin.config.InstantToSecondsSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 视频报表汇总响应DTO
 * Video Report Summary Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "视频报表汇总响应")
public class VideoReportSummaryResponse {

    @Schema(description = "日期(时间戳-秒)", example = "1770595200")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant date;

    @Schema(description = "总点击次数", example = "12560")
    private Long totalClickCount;

    @Schema(description = "总有效次数", example = "11230")
    private Long totalValidCount;

    @Schema(description = "总应结算金额(钻石)", example = "0")
    private Long totalSettlementAmount;
}
