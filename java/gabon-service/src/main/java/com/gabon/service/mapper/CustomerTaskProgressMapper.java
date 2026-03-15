package com.gabon.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gabon.service.model.entity.CustomerTaskProgress;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户任务进度Mapper
 * MyBatis映射器，用于操作customer_task_progress表
 */
@Mapper
public interface CustomerTaskProgressMapper extends BaseMapper<CustomerTaskProgress> {
}
