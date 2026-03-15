package com.gabon.service.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gabon.common.model.BaseDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * 任务定义实体
 * 映射到task_definitions表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task_definitions")
@Schema(description = "任务定义实体")
public class TaskDefinition extends BaseDO {

    @Schema(description = "唯一任务代码", example = "DAILY_WATCH_50")
    private String taskCode;

    @Schema(description = "任务名称", example = "观看50个视频")
    private String taskName;

    @Schema(description = "任务描述", example = "每日观看50个视频即可完成任务")
    private String taskDescription;

    @Schema(description = "任务类型: 1=每日, 2=每周, 3=每月", example = "1")
    private Integer taskType;

    @Schema(description = "任务分类: 1=watch_video(观看短剧), 2=upload_video, 3=share_video, 4=comment, 5=like, 6=login, 7=invite_friend, 8=watch_ad(观看广告)", example = "1")
    private Integer taskCategory;

    @Schema(description = "完成目标数量", example = "50")
    private Integer targetCount;

    @Schema(description = "奖励钻石", example = "200")
    private Integer rewardDiamonds;

    @Schema(description = "任务图标URL", example = "/icons/tasks/watch_video.png")
    private String iconUrl;

    @Schema(description = "显示顺序（数字越小优先级越高）", example = "1")
    private Integer displayOrder;

    @Schema(description = "任务开始时间（NULL表示始终可用）")
    private Instant startTime;

    @Schema(description = "任务结束时间（NULL表示不过期）")
    private Instant endTime;

    @Schema(description = "任务状态: 0=禁用, 1=启用", example = "1")
    private Integer status;

    @Schema(description = "仅VIP: 0=所有用户, 1=仅VIP", example = "0")
    private Integer vipOnly;

    @Schema(description = "最低用户等级要求", example = "0")
    private Integer minLevel;
}
