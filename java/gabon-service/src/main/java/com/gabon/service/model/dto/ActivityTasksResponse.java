package com.gabon.service.model.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 活动任务聚合响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "活动任务聚合响应")
public class ActivityTasksResponse {

    @Schema(description = "签到信息")
    private SignInInfo signIn;

    @Schema(description = "任务进度列表")
    private List<TaskProgressResponse> tasks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "签到信息")
    public static class SignInInfo {

        @Schema(description = "本月累计签到天数", example = "3")
        private Integer signInDays;

        @Schema(description = "今日是否已签到", example = "true")
        private Boolean todaySignedIn;
    }
}
