package com.gabon.service.controller;

import com.gabon.common.util.JsonData;
import com.gabon.service.config.AuthInterceptor;
import com.gabon.service.model.dto.ActivityTasksResponse;
import com.gabon.service.model.dto.SignInResponse;
import com.gabon.service.model.entity.Customer;
import com.gabon.service.service.SignInService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 活动中心控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/activity")
@RequiredArgsConstructor
@Tag(name = "活动中心", description = "签到、任务进度、活动奖励相关接口")
public class ActivityController {

    private final SignInService signInService;

    @PostMapping("/sign-in")
    @Operation(summary = "每日签到", description = "每日签到，获得日签钻石(1💎)，累计签到达到里程碑额外奖励",
            security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "签到成功"),
            @ApiResponse(responseCode = "401", description = "未认证或令牌无效"),
            @ApiResponse(responseCode = "400", description = "今天已签到")
    })
    public JsonData<SignInResponse> signIn() {
        Customer customer = AuthInterceptor.threadLocal.get();
        Long customerId = customer.getId();

        log.info("Daily sign-in request for customer: {}", customerId);

        SignInResponse response = signInService.doSignIn(customerId);
        return JsonData.buildSuccess(response);
    }

    @GetMapping("/tasks")
    @Operation(summary = "获取活动任务列表", description = "获取活动页面所有数据：签到状态+里程碑+任务进度（自动分配任务）",
            security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "401", description = "未认证或令牌无效")
    })
    public JsonData<ActivityTasksResponse> getActivityTasks() {
        Customer customer = AuthInterceptor.threadLocal.get();
        Long customerId = customer.getId();

        log.info("Getting activity tasks for customer: {}", customerId);

        ActivityTasksResponse response = signInService.getActivityTasks(customerId);
        return JsonData.buildSuccess(response);
    }
}
