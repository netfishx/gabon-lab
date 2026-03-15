package com.gabon.service.service.impl;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabon.common.enums.BizCodeEnum;
import com.gabon.common.enums.RewardStatus;
import com.gabon.common.enums.TaskStatus;
import com.gabon.common.enums.TransactionTypeEnum;
import com.gabon.common.exception.BizException;
import com.gabon.service.mapper.CustomerTaskProgressMapper;
import com.gabon.service.model.dto.ClaimRewardResponse;
import com.gabon.service.model.entity.CustomerTaskProgress;
import com.gabon.service.service.CustomerTransactionService;
import com.gabon.service.service.TaskRewardService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 任务奖励服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskRewardServiceImpl implements TaskRewardService {
    @Autowired
    CustomerTaskProgressMapper progressMapper;
    @Autowired
    CustomerTransactionService customerTransactionService;

    @Override
    @Transactional
    public ClaimRewardResponse claimReward(Long customerId, Long progressId) {
        // 获取进度记录
        CustomerTaskProgress progress = progressMapper.selectById(progressId);

        if (progress == null) {
            log.warn("Task progress not found: {}", progressId);
            throw new BizException(BizCodeEnum.OPS_REPEAT);
        }

        // 验证所有权
        if (!progress.getCustomerId().equals(customerId)) {
            log.warn("Customer {} attempted to claim reward for progress {} owned by {}",
                    customerId, progressId, progress.getCustomerId());
            throw new BizException(BizCodeEnum.OPS_REPEAT);
        }

        // 验证奖励是否可领取
        if (!progress.getRewardStatus().equals(RewardStatus.CLAIMABLE.getCode())) {
            log.warn("Reward not claimable: progressId={}, status={}", progressId, progress.getRewardStatus());
            throw new BizException(BizCodeEnum.OPS_REPEAT);
        }

        // 发放钻石奖励并记录交易
        Long newBalance = customerTransactionService.addDiamondTransaction(
                customerId, TransactionTypeEnum.TASK_REWARD,
                Long.valueOf(progress.getRewardDiamonds()), "任务奖励: " + progress.getTaskCode(),
                "TASK-" + progress.getId());

        // 更新进度状态
        progress.setTaskStatus(TaskStatus.CLAIMED.getCode());
        progress.setRewardStatus(RewardStatus.CLAIMED.getCode());
        progress.setClaimedTime(Instant.now());
        progressMapper.updateById(progress);

        log.info("Reward claimed: customer={}, progressId={}, diamonds={}, newBalance={}",
                customerId, progressId, progress.getRewardDiamonds(), newBalance);

        // 在事务内构建响应
        return ClaimRewardResponse.builder()
                .diamondsClaimed(progress.getRewardDiamonds())
                .newBalance(newBalance)
                .taskCode(progress.getTaskCode())
                .taskName(progress.getTaskCode()) // taskCode used as name (TaskDefinition not loaded here)
                .build();
    }
}
