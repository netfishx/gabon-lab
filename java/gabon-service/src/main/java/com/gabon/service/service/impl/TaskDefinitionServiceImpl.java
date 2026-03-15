package com.gabon.service.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gabon.service.mapper.CustomerMapper;
import com.gabon.service.mapper.TaskDefinitionMapper;
import com.gabon.service.model.entity.Customer;
import com.gabon.service.model.entity.TaskDefinition;
import com.gabon.service.service.TaskDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 任务定义服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskDefinitionServiceImpl implements TaskDefinitionService {
    @Autowired
    TaskDefinitionMapper taskDefinitionMapper;
    @Autowired
    CustomerMapper customerMapper;

    @Override
    public List<TaskDefinition> getActiveTasksByType(Integer taskType) {
        LambdaQueryWrapper<TaskDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskDefinition::getStatus, 1) // 启用
                .isNull(TaskDefinition::getDeletedFlag);

        if (taskType != null) {
            wrapper.eq(TaskDefinition::getTaskType, taskType);
        }

        wrapper.orderByAsc(TaskDefinition::getDisplayOrder);

        return taskDefinitionMapper.selectList(wrapper);
    }

    @Override
    public List<TaskDefinition> getActiveTasksForCustomer(Long customerId, Integer taskType) {
        // 获取客户信息以检查VIP状态
        Customer customer = customerMapper.selectById(customerId);
        if (customer == null) {
            log.warn("Customer not found: {}", customerId);
            return List.of();
        }

        LambdaQueryWrapper<TaskDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskDefinition::getStatus, 1) // 启用
                .isNull(TaskDefinition::getDeletedFlag);

        if (taskType != null) {
            wrapper.eq(TaskDefinition::getTaskType, taskType);
        }

        // 根据VIP状态过滤：VIP显示所有任务，普通用户只显示非VIP任务
        if (customer.getIsVip() == 0) {
            wrapper.eq(TaskDefinition::getVipOnly, 0);
        }

        wrapper.orderByAsc(TaskDefinition::getDisplayOrder);

        return taskDefinitionMapper.selectList(wrapper);
    }

    @Override
    public TaskDefinition getTaskById(Long taskId) {
        return taskDefinitionMapper.selectOne(
                new LambdaQueryWrapper<TaskDefinition>()
                        .eq(TaskDefinition::getId, taskId)
                        .isNull(TaskDefinition::getDeletedFlag));
    }

    @Override
    public TaskDefinition getTaskByCode(String taskCode) {
        return taskDefinitionMapper.selectOne(
                new LambdaQueryWrapper<TaskDefinition>()
                        .eq(TaskDefinition::getTaskCode, taskCode)
                        .isNull(TaskDefinition::getDeletedFlag));
    }
}
