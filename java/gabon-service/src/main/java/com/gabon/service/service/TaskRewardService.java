package com.gabon.service.service;

import com.gabon.service.model.dto.ClaimRewardResponse;

/**
 * 任务奖励服务
 */
public interface TaskRewardService {

    /**
     * 领取已完成任务的奖励
     *
     * @param customerId 客户ID
     * @param progressId 进度ID
     * @return 包含领取的钻石、新余额和任务信息的响应
     */
    ClaimRewardResponse claimReward(Long customerId, Long progressId);
}
