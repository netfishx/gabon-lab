package com.gabon.service.service;

import com.gabon.service.model.dto.ActivityTasksResponse;
import com.gabon.service.model.dto.SignInResponse;

/**
 * 签到服务接口
 */
public interface SignInService {

    /**
     * 每日签到
     * - 检查今日是否已签到
     * - 插入签到记录
     * - 发放日签钻石(1💎) + 里程碑奖励
     * - 更新客户余额
     *
     * @param customerId 客户ID
     * @return 签到结果
     */
    SignInResponse doSignIn(Long customerId);

    /**
     * 获取活动任务聚合数据
     * - 签到状态 + 里程碑信息
     * - 自动分配并获取任务进度
     *
     * @param customerId 客户ID
     * @return 活动任务聚合响应
     */
    ActivityTasksResponse getActivityTasks(Long customerId);
}
