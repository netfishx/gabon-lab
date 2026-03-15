package com.gabon.service.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务进度响应DTO — 仅返回前端展示所需的最小字段
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "任务进度响应")
public class TaskProgressResponse {

    @Schema(description = "进度ID", example = "1")
    private Long progressId;

    @Schema(description = "任务ID", example = "1")
    private Long taskId;

    @Schema(description = "任务代码，用于前端识别任务类型", example = "DAILY_WATCH_VIDEO_5")
    private String taskCode;

    @Schema(description = "当前进度", example = "2")
    private Integer currentCount;

    @Schema(description = "目标数量", example = "5")
    private Integer targetCount;

    @Schema(description = "任务状态: 1=进行中, 3=已完成并领取", example = "1")
    private Integer taskStatus;

    @Schema(description = "奖励钻石", example = "50")
    private Integer rewardDiamonds;
}
