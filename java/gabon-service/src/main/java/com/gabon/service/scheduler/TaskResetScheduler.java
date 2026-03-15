package com.gabon.service.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gabon.service.mapper.CustomerTaskProgressMapper;
import com.gabon.service.model.entity.CustomerTaskProgress;
import com.gabon.common.enums.RewardStatus;
import com.gabon.common.enums.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.List;

/**
 * 任务重置调度器
 * 统一的每日任务，处理所有任务重置（每日、每周、每月）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskResetScheduler {

    private final CustomerTaskProgressMapper progressMapper;

    /**
     * 统一的每日任务，在北京时间00:00:05执行
     * - 重置每日任务（每天）
     * - 重置每周任务（仅在周一）
     * - 重置每月任务（仅在每月1号）
     */
    @Scheduled(cron = "5 0 0 * * ?", zone = "Asia/Shanghai") // 每天北京时间00:00:05执行
    @Transactional
    public void resetTasks() {
        log.info("Starting unified task reset job");

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        LocalDate yesterday = today.minusDays(1);

        // 始终重置每日任务
        resetDailyTasks(yesterday);

        // 在周一重置每周任务（星期几 = 1）
        if (today.getDayOfWeek().getValue() == 1) {
            log.info("Monday detected, resetting weekly tasks");
            resetWeeklyTasks(yesterday);
        }

        // 在每月1号重置每月任务
        if (today.getDayOfMonth() == 1) {
            log.info("First day of month detected, resetting monthly tasks");
            resetMonthlyTasks(yesterday);
        }

        // 标记过期的未领取任务
        markExpiredTasks();

        log.info("Unified task reset job completed");
    }

    /**
     * 重置每日任务
     */
    private void resetDailyTasks(LocalDate yesterday) {
        String yesterdayKey = yesterday.toString();

        LambdaQueryWrapper<CustomerTaskProgress> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CustomerTaskProgress::getPeriodKey, yesterdayKey)
                .in(CustomerTaskProgress::getTaskStatus, TaskStatus.IN_PROGRESS.getCode(),
                        TaskStatus.COMPLETED.getCode())
                .isNull(CustomerTaskProgress::getDeletedFlag);

        List<CustomerTaskProgress> expiredTasks = progressMapper.selectList(wrapper);

        for (CustomerTaskProgress task : expiredTasks) {
            if (task.getTaskStatus().equals(TaskStatus.COMPLETED.getCode())
                    && task.getRewardStatus().equals(RewardStatus.CLAIMABLE.getCode())) {
                // 已完成但未领取 - 标记为过期
                task.setTaskStatus(TaskStatus.EXPIRED.getCode());
                task.setRewardStatus(RewardStatus.NOT_CLAIMABLE.getCode());
            } else {
                // 未完成 - 仅标记为过期
                task.setTaskStatus(TaskStatus.EXPIRED.getCode());
            }
            progressMapper.updateById(task);
        }

        log.info("Reset {} daily tasks from period: {}", expiredTasks.size(), yesterdayKey);
    }

    /**
     * 重置每周任务
     */
    private void resetWeeklyTasks(LocalDate yesterday) {
        int year = yesterday.getYear();
        int week = yesterday.get(WeekFields.ISO.weekOfWeekBasedYear());
        String lastWeekKey = String.format("%d-W%02d", year, week);

        LambdaQueryWrapper<CustomerTaskProgress> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CustomerTaskProgress::getPeriodKey, lastWeekKey)
                .in(CustomerTaskProgress::getTaskStatus, TaskStatus.IN_PROGRESS.getCode(),
                        TaskStatus.COMPLETED.getCode())
                .isNull(CustomerTaskProgress::getDeletedFlag);

        List<CustomerTaskProgress> expiredTasks = progressMapper.selectList(wrapper);

        for (CustomerTaskProgress task : expiredTasks) {
            if (task.getTaskStatus().equals(TaskStatus.COMPLETED.getCode())
                    && task.getRewardStatus().equals(RewardStatus.CLAIMABLE.getCode())) {
                task.setTaskStatus(TaskStatus.EXPIRED.getCode());
                task.setRewardStatus(RewardStatus.NOT_CLAIMABLE.getCode());
            } else {
                task.setTaskStatus(TaskStatus.EXPIRED.getCode());
            }
            progressMapper.updateById(task);
        }

        log.info("Reset {} weekly tasks from period: {}", expiredTasks.size(), lastWeekKey);
    }

    /**
     * 重置每月任务
     */
    private void resetMonthlyTasks(LocalDate yesterday) {
        String lastMonthKey = String.format("%d-%02d", yesterday.getYear(), yesterday.getMonthValue());

        LambdaQueryWrapper<CustomerTaskProgress> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CustomerTaskProgress::getPeriodKey, lastMonthKey)
                .in(CustomerTaskProgress::getTaskStatus, TaskStatus.IN_PROGRESS.getCode(),
                        TaskStatus.COMPLETED.getCode())
                .isNull(CustomerTaskProgress::getDeletedFlag);

        List<CustomerTaskProgress> expiredTasks = progressMapper.selectList(wrapper);

        for (CustomerTaskProgress task : expiredTasks) {
            if (task.getTaskStatus().equals(TaskStatus.COMPLETED.getCode())
                    && task.getRewardStatus().equals(RewardStatus.CLAIMABLE.getCode())) {
                task.setTaskStatus(TaskStatus.EXPIRED.getCode());
                task.setRewardStatus(RewardStatus.NOT_CLAIMABLE.getCode());
            } else {
                task.setTaskStatus(TaskStatus.EXPIRED.getCode());
            }
            progressMapper.updateById(task);
        }

        log.info("Reset {} monthly tasks from period: {}", expiredTasks.size(), lastMonthKey);
    }

    /**
     * 标记过期的未领取任务
     */
    private void markExpiredTasks() {
        Instant now = Instant.now();

        LambdaQueryWrapper<CustomerTaskProgress> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(CustomerTaskProgress::getPeriodEndTime, now)
                .in(CustomerTaskProgress::getTaskStatus, TaskStatus.IN_PROGRESS.getCode(),
                        TaskStatus.COMPLETED.getCode())
                .isNull(CustomerTaskProgress::getDeletedFlag);

        List<CustomerTaskProgress> expiredTasks = progressMapper.selectList(wrapper);

        for (CustomerTaskProgress task : expiredTasks) {
            task.setTaskStatus(TaskStatus.EXPIRED.getCode());
            if (task.getRewardStatus().equals(RewardStatus.CLAIMABLE.getCode())) {
                task.setRewardStatus(RewardStatus.NOT_CLAIMABLE.getCode());
            }
            progressMapper.updateById(task);
        }

        log.info("Marked {} expired tasks", expiredTasks.size());
    }
}
