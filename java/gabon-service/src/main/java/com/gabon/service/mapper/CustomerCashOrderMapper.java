package com.gabon.service.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gabon.service.model.entity.CustomerCashOrder;

@Mapper
public interface CustomerCashOrderMapper extends BaseMapper<CustomerCashOrder> {
}
