package com.gabon.service.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 签到响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "签到响应")
public class SignInResponse {

    @Schema(description = "本月累计签到天数", example = "3")
    private Integer signInDays;

    @Schema(description = "今日是否已签到", example = "true")
    private Boolean todaySignedIn;

    @Schema(description = "本次签到获得的钻石（日签+里程碑）", example = "67")
    private Integer diamondsAwarded;

    @Schema(description = "命中的里程碑天数（未命中为null）", example = "7")
    private Integer milestoneHit;

    @Schema(description = "更新后的钻石余额", example = "1067")
    private Long newDiamondBalance;
}
