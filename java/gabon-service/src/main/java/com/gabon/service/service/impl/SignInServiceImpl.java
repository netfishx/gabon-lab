package com.gabon.service.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gabon.common.enums.BizCodeEnum;
import com.gabon.common.enums.TaskStatus;
import com.gabon.common.enums.TransactionTypeEnum;
import com.gabon.common.exception.BizException;
import com.gabon.service.mapper.ActivityRewardConfigMapper;
import com.gabon.service.mapper.CustomerSignInRecordMapper;
import com.gabon.service.mapper.TaskDefinitionMapper;
import com.gabon.service.model.dto.ActivityTasksResponse;
import com.gabon.service.model.dto.SignInResponse;
import com.gabon.service.model.dto.TaskProgressResponse;
import com.gabon.service.model.entity.ActivityRewardConfig;
import com.gabon.service.model.entity.CustomerSignInRecord;
import com.gabon.service.model.entity.CustomerTaskProgress;
import com.gabon.service.model.entity.TaskDefinition;
import com.gabon.service.service.CustomerTransactionService;
import com.gabon.service.service.SignInService;
import com.gabon.service.service.TaskProgressService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 签到服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignInServiceImpl implements SignInService {

    private final CustomerSignInRecordMapper signInRecordMapper;
    private final ActivityRewardConfigMapper rewardConfigMapper;
    private final TaskProgressService taskProgressService;
    private final TaskDefinitionMapper taskDefinitionMapper;
    private final CustomerTransactionService customerTransactionService;

    private static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");

    @Override
    @Transactional
    public SignInResponse doSignIn(Long customerId) {
        LocalDate today = LocalDate.now(BEIJING_ZONE);
        String periodKey = String.format("%d-%02d", today.getYear(), today.getMonthValue());

        // 1. Check if already signed in today
        LambdaQueryWrapper<CustomerSignInRecord> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(CustomerSignInRecord::getCustomerId, customerId)
                .eq(CustomerSignInRecord::getSignInDate, today);

        if (signInRecordMapper.selectCount(checkWrapper) > 0) {
            throw new BizException(BizCodeEnum.SIGN_IN_ALREADY_TODAY);
        }

        // 2. Get daily sign-in reward from config
        int dailyReward = getDailySignInReward();

        // 3. Count current month sign-in days (before today)
        LambdaQueryWrapper<CustomerSignInRecord> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(CustomerSignInRecord::getCustomerId, customerId)
                .eq(CustomerSignInRecord::getPeriodKey, periodKey);
        long previousDays = signInRecordMapper.selectCount(countWrapper);
        int newDayCount = (int) previousDays + 1;

        // 4. Check if a milestone is hit
        Integer milestoneHit = null;
        int milestoneReward = 0;
        List<ActivityRewardConfig> milestones = getSignInMilestones();
        for (ActivityRewardConfig milestone : milestones) {
            int requiredDays = Integer.parseInt(milestone.getConfigKey());
            if (newDayCount == requiredDays) {
                milestoneHit = requiredDays;
                milestoneReward = milestone.getRewardDiamonds();
                break;
            }
        }

        int totalDiamonds = dailyReward + milestoneReward;

        // 5. Insert sign-in record
        CustomerSignInRecord record = new CustomerSignInRecord();
        record.setCustomerId(customerId);
        record.setSignInDate(today);
        record.setPeriodKey(periodKey);
        record.setDiamondsAwarded(totalDiamonds);
        record.setCreateBy("system");
        record.setCreateTime(Instant.now());
        signInRecordMapper.insert(record);

        // 6. Award diamonds and record transaction
        String remark = "签到奖励 (日签)";
        if (milestoneHit != null) {
            remark = String.format("签到奖励 (日签+连续%d天额外奖励)", milestoneHit);
        }
        long newBalance = customerTransactionService.addDiamondTransaction(
                customerId, TransactionTypeEnum.SIGN_IN_REWARD, (long) totalDiamonds, remark,
                "SIGNIN-" + record.getId());

        log.info("Sign-in success: customer={}, day={}, dailyReward={}, milestoneReward={}, newBalance={}",
                customerId, newDayCount, dailyReward, milestoneReward, newBalance);

        return SignInResponse.builder()
                .signInDays(newDayCount)
                .todaySignedIn(true)
                .diamondsAwarded(totalDiamonds)
                .milestoneHit(milestoneHit)
                .newDiamondBalance(newBalance)
                .build();
    }

    @Override
    @Transactional
    public ActivityTasksResponse getActivityTasks(Long customerId) {
        LocalDate today = LocalDate.now(BEIJING_ZONE);
        String periodKey = String.format("%d-%02d", today.getYear(), today.getMonthValue());

        // --- Sign-in info ---
        // Count sign-in days this month
        LambdaQueryWrapper<CustomerSignInRecord> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(CustomerSignInRecord::getCustomerId, customerId)
                .eq(CustomerSignInRecord::getPeriodKey, periodKey);
        int signInDays = Math.toIntExact(signInRecordMapper.selectCount(countWrapper));

        // Check if signed in today
        LambdaQueryWrapper<CustomerSignInRecord> todayWrapper = new LambdaQueryWrapper<>();
        todayWrapper.eq(CustomerSignInRecord::getCustomerId, customerId)
                .eq(CustomerSignInRecord::getSignInDate, today);
        boolean todaySignedIn = signInRecordMapper.selectCount(todayWrapper) > 0;

        ActivityTasksResponse.SignInInfo signInInfo = ActivityTasksResponse.SignInInfo.builder()
                .signInDays(signInDays)
                .todaySignedIn(todaySignedIn)
                .build();

        // --- Task progress (auto-assigns daily tasks if needed) ---
        List<CustomerTaskProgress> progressList = taskProgressService.getCustomerTaskProgress(customerId, null);

        // Build task responses
        Map<Long, TaskDefinition> taskMap = new HashMap<>();
        for (CustomerTaskProgress progress : progressList) {
            if (!taskMap.containsKey(progress.getTaskId())) {
                TaskDefinition task = taskDefinitionMapper.selectById(progress.getTaskId());
                if (task != null) {
                    taskMap.put(task.getId(), task);
                }
            }
        }

        List<TaskProgressResponse> taskResponses = progressList.stream()
                .map(progress -> {
                    TaskDefinition task = taskMap.get(progress.getTaskId());
                    if (task == null) return null;
                    return TaskProgressResponse.builder()
                            .progressId(progress.getId())
                            .taskId(task.getId())
                            .taskCode(task.getTaskCode())
                            .currentCount(progress.getCurrentCount())
                            .targetCount(progress.getTargetCount())
                            .taskStatus(progress.getTaskStatus())
                            .rewardDiamonds(progress.getRewardDiamonds())
                            .build();
                })
                .filter(Objects::nonNull)
                .filter(r -> !r.getTaskStatus().equals(TaskStatus.EXPIRED.getCode()))
                .collect(Collectors.toList());

        return ActivityTasksResponse.builder()
                .signIn(signInInfo)
                .tasks(taskResponses)
                .build();
    }

    /**
     * Get daily sign-in reward from config (defaults to 1)
     */
    private int getDailySignInReward() {
        LambdaQueryWrapper<ActivityRewardConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ActivityRewardConfig::getConfigType, "DAILY_SIGN_IN")
                .eq(ActivityRewardConfig::getConfigKey, "daily")
                .eq(ActivityRewardConfig::getStatus, 1)
                .isNull(ActivityRewardConfig::getDeletedFlag);

        ActivityRewardConfig config = rewardConfigMapper.selectOne(wrapper);
        return config != null ? config.getRewardDiamonds() : 1;
    }

    /**
     * Get all sign-in milestone configs, sorted by required days
     */
    private List<ActivityRewardConfig> getSignInMilestones() {
        LambdaQueryWrapper<ActivityRewardConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ActivityRewardConfig::getConfigType, "SIGN_IN_MILESTONE")
                .eq(ActivityRewardConfig::getStatus, 1)
                .isNull(ActivityRewardConfig::getDeletedFlag)
                .orderByAsc(ActivityRewardConfig::getDisplayOrder);

        return rewardConfigMapper.selectList(wrapper);
    }
}
