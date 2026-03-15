package com.gabon.service.service;

import com.gabon.service.model.entity.TaskDefinition;

import java.util.List;

/**
 * 任务定义服务
 */
public interface TaskDefinitionService {

    /**
     * 根据类型获取所有活跃任务
     *
     * @param taskType 任务类型：1=每日, 2=每周, 3=每月
     * @return 活跃任务定义列表
     */
    List<TaskDefinition> getActiveTasksByType(Integer taskType);

    /**
     * 获取客户的所有活跃任务（考虑VIP状态）
     *
     * @param customerId 客户ID
     * @param taskType    任务类型（null表示所有类型）
     * @return 活跃任务定义列表
     */
    List<TaskDefinition> getActiveTasksForCustomer(Long customerId, Integer taskType);

    /**
     * 根据ID获取任务定义
     *
     * @param taskId 任务ID
     * @return 任务定义
     */
    TaskDefinition getTaskById(Long taskId);

    /**
     * 根据代码获取任务定义
     *
     * @param taskCode 任务代码
     * @return 任务定义
     */
    TaskDefinition getTaskByCode(String taskCode);
}
