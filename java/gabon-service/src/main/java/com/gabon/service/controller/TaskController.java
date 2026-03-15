package com.gabon.service.controller;

import com.gabon.common.util.JsonData;
import com.gabon.service.config.AuthInterceptor;
import com.gabon.service.model.dto.ClaimRewardResponse;
import com.gabon.service.model.entity.Customer;
import com.gabon.service.service.TaskRewardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 任务控制器
 *
 * 当前活动中心 UI 的任务数据由 ActivityController(/api/activity/tasks) 统一返回。
 * 本控制器只负责用户行为上报（观看广告等），触发任务进度更新，完成后自动发放钻石。
 *
 * NOTE: /claim 端点保留注释，待后续改为用户手动领取奖励时启用。
 */
@Slf4j
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@Tag(name = "任务上报", description = "用户行为上报接口，触发任务进度更新并自动发放奖励")
public class TaskController {

    @Autowired
    TaskRewardService taskRewardService;

    // ============================================================
    // NOTE: GET /list is removed — task list is served by
    //       ActivityController GET /api/activity/tasks
    // ============================================================

    @PostMapping("/claim/{progressId}")
    @Operation(summary = "手动领取任务奖励", description = "领取已完成任务的奖励。当自动发奖失败时，可调用此接口补领。",
            security = @SecurityRequirement(name = "Bearer"))
    public JsonData<ClaimRewardResponse> claimReward(@PathVariable Long progressId) {
        Customer customer = AuthInterceptor.threadLocal.get();
        Long customerId = customer.getId();
        ClaimRewardResponse response = taskRewardService.claimReward(customerId, progressId);
        return JsonData.buildSuccess(response);
    }
}
