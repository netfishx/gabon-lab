package com.gabon.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gabon.service.model.entity.TaskDefinition;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务定义Mapper
 * MyBatis映射器，用于操作task_definitions表
 */
@Mapper
public interface TaskDefinitionMapper extends BaseMapper<TaskDefinition> {
}
