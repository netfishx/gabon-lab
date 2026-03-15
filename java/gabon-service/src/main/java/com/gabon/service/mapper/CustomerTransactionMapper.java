package com.gabon.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gabon.service.model.entity.CustomerTransaction;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户交易记录 Mapper 接口
 */
@Mapper
public interface CustomerTransactionMapper extends BaseMapper<CustomerTransaction> {
}
