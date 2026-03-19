package com.gabon.service.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gabon.common.enums.RewardStatus;
import com.gabon.common.enums.TaskStatus;
import com.gabon.common.enums.TransactionTypeEnum;
import com.gabon.service.mapper.CustomerTaskProgressMapper;
import com.gabon.service.mapper.TaskDefinitionMapper;
import com.gabon.service.model.entity.CustomerTaskProgress;
import com.gabon.service.model.entity.TaskDefinition;
import com.gabon.service.service.CustomerTransactionService;
import com.gabon.service.service.TaskProgressService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 任务进度服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskProgressServiceImpl implements TaskProgressService {
    @Autowired
    CustomerTaskProgressMapper progressMapper;
    @Autowired
    TaskDefinitionMapper taskDefinitionMapper;
    @Autowired
    CustomerTransactionService customerTransactionService;

    @Override
    @Transactional
    public List<CustomerTaskProgress> getCustomerTaskProgress(Long customerId, Integer taskType) {
        // 1. 检查客户是否有活跃的每日任务
        String todayKey = getPeriodKey(1); // 1 = 每日任务
        LambdaQueryWrapper<CustomerTaskProgress> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(CustomerTaskProgress::getCustomerId, customerId)
                .eq(CustomerTaskProgress::getPeriodKey, todayKey)
                .isNull(CustomerTaskProgress::getDeletedFlag);

        long count = progressMapper.selectCount(checkWrapper);

        // 如果没有每日任务，则自动分配 (从 task_definitions 表中查找活跃的每日任务，如观看短剧、观看广告)
        if (count == 0) {
            log.info("No daily tasks found for customer {}, auto-assigning from task_definitions", customerId);
            autoAssignDailyTasks(customerId);
        }

        // 2. 获取客户的任务进度 (返回所有未删除的任务)
        LambdaQueryWrapper<CustomerTaskProgress> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CustomerTaskProgress::getCustomerId, customerId)
                .isNull(CustomerTaskProgress::getDeletedFlag)
                .orderByDesc(CustomerTaskProgress::getCreateTime);

        return progressMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public void updateWatchVideoProgress(Long customerId, Long videoId) {
        // FIXME 暂时只有每日视频任务

        String dailyKey = getPeriodKey(1);
        // String weeklyKey = getPeriodKey(2);
        // String monthlyKey = getPeriodKey(3);

        // 更新每日观看视频任务
        updateWatchTaskProgress(customerId, 1, dailyKey);

        // 更新每周观看视频任务
        // updateWatchTaskProgress(customerId, 2, weeklyKey);

        // 更新每月观看视频任务
        // updateWatchTaskProgress(customerId, 3, monthlyKey);

        log.info("Updated watch video progress for customer: {}, video: {}", customerId, videoId);
    }

    @Override
    @Transactional
    public void updateWatchAdProgress(Long customerId) {
        String dailyKey = getPeriodKey(1);
        // 广告任务只有每日类型
        updateTaskProgressByCategory(customerId, 1, dailyKey, 8); // 8 = watch_ad
        log.info("Updated watch ad progress for customer: {}", customerId);
    }

    private void updateWatchTaskProgress(Long customerId, Integer taskType, String periodKey) {
        updateTaskProgressByCategory(customerId, taskType, periodKey, 1); // 1 = watch_video
    }

    private void updateTaskProgressByCategory(Long customerId, Integer taskType, String periodKey, Integer taskCategory) {
        // 获取该类型的所有活跃观看视频任务
        LambdaQueryWrapper<TaskDefinition> taskWrapper = new LambdaQueryWrapper<>();
        taskWrapper.eq(TaskDefinition::getTaskType, taskType)
                .eq(TaskDefinition::getTaskCategory, taskCategory)
                .eq(TaskDefinition::getStatus, 1)
                .isNull(TaskDefinition::getDeletedFlag);

        List<TaskDefinition> watchTasks = taskDefinitionMapper.selectList(taskWrapper);

        for (TaskDefinition task : watchTasks) {
            // 获取或创建进度记录
            LambdaQueryWrapper<CustomerTaskProgress> progressWrapper = new LambdaQueryWrapper<>();
            progressWrapper.eq(CustomerTaskProgress::getCustomerId, customerId)
                    .eq(CustomerTaskProgress::getTaskId, task.getId())
                    .eq(CustomerTaskProgress::getPeriodKey, periodKey)
                    .isNull(CustomerTaskProgress::getDeletedFlag);

            CustomerTaskProgress progress = progressMapper.selectOne(progressWrapper);

            if (progress == null) {
                // 创建新的进度记录
                progress = createProgressRecord(customerId, task, periodKey);
                progressMapper.insert(progress);
            }

            // 如果尚未完成，则更新进度
            if (progress.getTaskStatus() < TaskStatus.COMPLETED.getCode()) {
                progress.setCurrentCount(progress.getCurrentCount() + 1);

                boolean completedNow = progress.getCurrentCount() >= progress.getTargetCount();
                if (completedNow) {
                    progress.setTaskStatus(TaskStatus.COMPLETED.getCode());
                    progress.setRewardStatus(RewardStatus.CLAIMABLE.getCode());
                    progress.setCompletedTime(Instant.now());
                }

                progressMapper.updateById(progress);
                log.info("Updated task progress: customer={}, task={}, progress={}/{}",
                        customerId, task.getTaskCode(), progress.getCurrentCount(), progress.getTargetCount());

                if (completedNow) {
                    tryAutoClaimReward(customerId, progress);
                }
            }
        }
    }


    @Override
    @Transactional
    public void autoAssignDailyTasks(Long customerId) {
        // 获取所有活跃的每日任务 (task_type = 1, status = 1)
        LambdaQueryWrapper<TaskDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskDefinition::getTaskType, 1) // Daily
                .eq(TaskDefinition::getStatus, 1)
                .isNull(TaskDefinition::getDeletedFlag);

        List<TaskDefinition> dailyTasks = taskDefinitionMapper.selectList(wrapper);
        String periodKey = getPeriodKey(1);

        for (TaskDefinition task : dailyTasks) {
            // 检查进度是否已存在
            LambdaQueryWrapper<CustomerTaskProgress> checkWrapper = new LambdaQueryWrapper<>();
            checkWrapper.eq(CustomerTaskProgress::getCustomerId, customerId)
                    .eq(CustomerTaskProgress::getTaskId, task.getId())
                    .eq(CustomerTaskProgress::getPeriodKey, periodKey)
                    .isNull(CustomerTaskProgress::getDeletedFlag);

            if (progressMapper.selectCount(checkWrapper) == 0) {
                // 创建进度记录
                CustomerTaskProgress progress = createProgressRecord(customerId, task, periodKey);
                progressMapper.insert(progress);
                log.info("Auto-assigned daily task: customer={}, taskCode={}", customerId, task.getTaskCode());
            }
        }
    }

    @Override
    @Transactional
    public void checkAndUpdateCompletionStatus(Long progressId) {
        CustomerTaskProgress progress = progressMapper.selectById(progressId);
        if (progress == null) {
            return;
        }

        if (progress.getCurrentCount() >= progress.getTargetCount()
                && progress.getTaskStatus().equals(TaskStatus.IN_PROGRESS.getCode())) {
            progress.setTaskStatus(TaskStatus.COMPLETED.getCode());
            progress.setRewardStatus(RewardStatus.CLAIMABLE.getCode());
            progress.setCompletedTime(Instant.now());
            progressMapper.updateById(progress);
            log.info("Task completed: progressId={}, customer={}", progressId, progress.getCustomerId());
        }
    }

    /**
     * 创建新的进度记录
     */
    private CustomerTaskProgress createProgressRecord(Long customerId, TaskDefinition task, String periodKey) {
        CustomerTaskProgress progress = new CustomerTaskProgress();
        progress.setCustomerId(customerId);
        progress.setTaskId(task.getId());
        progress.setTaskCode(task.getTaskCode());
        progress.setCurrentCount(0);
        progress.setTargetCount(task.getTargetCount());
        progress.setPeriodKey(periodKey);

        // 根据任务类型设置周期时间
        setPeriodTimes(progress, task.getTaskType());

        progress.setTaskStatus(TaskStatus.IN_PROGRESS.getCode());
        progress.setRewardStatus(RewardStatus.NOT_CLAIMABLE.getCode());
        progress.setRewardDiamonds(task.getRewardDiamonds());

        return progress;
    }

    /**
     * 根据任务类型设置周期开始和结束时间
     */
    private void setPeriodTimes(CustomerTaskProgress progress, Integer taskType) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        ZoneId zoneId = ZoneId.of("Asia/Shanghai");

        switch (taskType) {
            case 1: // 每日
                progress.setPeriodStartTime(today.atStartOfDay(zoneId).toInstant());
                progress.setPeriodEndTime(today.plusDays(1).atStartOfDay(zoneId).minusNanos(1).toInstant());
                break;
            case 2: // 每周
                LocalDate weekStart = today.with(WeekFields.ISO.dayOfWeek(), 1);
                progress.setPeriodStartTime(weekStart.atStartOfDay(zoneId).toInstant());
                progress.setPeriodEndTime(weekStart.plusWeeks(1).atStartOfDay(zoneId).minusNanos(1).toInstant());
                break;
            case 3: // 每月
                LocalDate monthStart = today.withDayOfMonth(1);
                progress.setPeriodStartTime(monthStart.atStartOfDay(zoneId).toInstant());
                progress.setPeriodEndTime(monthStart.plusMonths(1).atStartOfDay(zoneId).minusNanos(1).toInstant());
                break;
        }
    }

    /**
     * 根据任务类型获取周期键
     */
    private String getPeriodKey(Integer taskType) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));

        switch (taskType) {
            case 1: // 每日
                return today.toString(); // 2026-02-05
            case 2: // 每周
                int year = today.getYear();
                int week = today.get(WeekFields.ISO.weekOfWeekBasedYear());
                return String.format("%d-W%02d", year, week); // 2026-W06
            case 3: // 每月
                return String.format("%d-%02d", today.getYear(), today.getMonthValue()); // 2026-02
            default:
                return today.toString();
        }
    }

    /**
     * 自动发放钻石到客户账户
     */
    private void autoAwardDiamonds(Long customerId, Integer diamonds, String taskCode, String transactionNo) {
        if (diamonds == null || diamonds <= 0) {
            return;
        }
        customerTransactionService.addDiamondTransaction(
                customerId, TransactionTypeEnum.TASK_REWARD, Long.valueOf(diamonds), "任务奖励: " + taskCode,
                transactionNo);
        log.info("Auto-awarded diamonds: customer={}, task={}, diamonds={}", customerId, taskCode, diamonds);
    }

    private void tryAutoClaimReward(Long customerId, CustomerTaskProgress progress) {
        try {
            autoAwardDiamonds(customerId, progress.getRewardDiamonds(), progress.getTaskCode(),
                    "TASK-" + progress.getId());
            progress.setTaskStatus(TaskStatus.CLAIMED.getCode());
            progress.setRewardStatus(RewardStatus.CLAIMED.getCode());
            progress.setClaimedTime(Instant.now());
            progressMapper.updateById(progress);
        } catch (Exception e) {
            log.error("Auto-claim failed after task completion: customer={}, progressId={}, taskCode={}",
                    customerId, progress.getId(), progress.getTaskCode(), e);
        }
    }
}
