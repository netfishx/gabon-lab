package com.gabon.service.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gabon.common.model.BaseDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * 客户任务进度实体
 * 映射到customer_task_progress表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("customer_task_progress")
@Schema(description = "客户任务进度实体")
public class CustomerTaskProgress extends BaseDO {

    @Schema(description = "客户ID", example = "1")
    private Long customerId;

    @Schema(description = "任务ID", example = "1")
    private Long taskId;

    @Schema(description = "任务代码（反规范化）", example = "DAILY_WATCH_50")
    private String taskCode;

    @Schema(description = "当前进度数量", example = "23")
    private Integer currentCount;

    @Schema(description = "目标数量（反规范化）", example = "50")
    private Integer targetCount;

    @Schema(description = "周期键（例如：每日=2026-02-05，每周=2026-W06）", example = "2026-02-05")
    private String periodKey;

    @Schema(description = "周期开始时间")
    private Instant periodStartTime;

    @Schema(description = "周期结束时间")
    private Instant periodEndTime;

    @Schema(description = "任务状态: 1=进行中, 2=已完成, 3=已领取, 4=已过期", example = "1")
    private Integer taskStatus;

    @Schema(description = "奖励状态: 0=不可领取, 1=可领取, 2=已领取", example = "0")
    private Integer rewardStatus;

    @Schema(description = "任务完成时间")
    private Instant completedTime;

    @Schema(description = "奖励领取时间")
    private Instant claimedTime;

    @Schema(description = "奖励钻石（反规范化）", example = "200")
    private Integer rewardDiamonds;
}
