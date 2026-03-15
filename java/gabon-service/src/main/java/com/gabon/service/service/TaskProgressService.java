package com.gabon.service.service;

import com.gabon.service.model.entity.CustomerTaskProgress;

import java.util.List;

/**
 * 任务进度服务
 */
public interface TaskProgressService {

    /**
     * 获取客户当前周期的任务进度
     *
     * @param customerId 客户ID
     * @param taskType   任务类型（null表示所有类型）
     * @return 任务进度列表
     */
    List<CustomerTaskProgress> getCustomerTaskProgress(Long customerId, Integer taskType);

    /**
     * 更新观看视频任务的进度
     * 仅统计有效播放（play_type=2，15秒以上）
     *
     * @param customerId 客户ID
     * @param videoId    视频ID
     */
    void updateWatchVideoProgress(Long customerId, Long videoId);

    /**
     * 更新观看广告任务的进度
     * 对应UI中的"观看广告赚钒石"任务
     *
     * @param customerId 客户ID
     */
    void updateWatchAdProgress(Long customerId);

    /**
     * 如果客户没有每日任务，则自动分配
     *
     * @param customerId 客户ID
     */
    void autoAssignDailyTasks(Long customerId);
    /**
     * 检查任务是否完成并更新奖励状态
     *
     * @param progressId 进度ID
     */
    void checkAndUpdateCompletionStatus(Long progressId);

}
