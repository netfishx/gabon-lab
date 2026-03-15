package com.gabon.service.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 领取奖励响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "领取奖励响应")
public class ClaimRewardResponse {

    @Schema(description = "领取的钻石", example = "200")
    private Integer diamondsClaimed;

    @Schema(description = "新的钻石余额", example = "1200")
    private Long newBalance;

    @Schema(description = "任务代码", example = "DAILY_WATCH_50")
    private String taskCode;

    @Schema(description = "任务名称", example = "观看50个视频")
    private String taskName;
}
