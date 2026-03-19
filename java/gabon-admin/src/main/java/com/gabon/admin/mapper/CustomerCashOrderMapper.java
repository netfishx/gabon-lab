package com.gabon.admin.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gabon.admin.model.entity.CustomerCashOrder;

@Mapper
public interface CustomerCashOrderMapper extends BaseMapper<CustomerCashOrder> {
}
