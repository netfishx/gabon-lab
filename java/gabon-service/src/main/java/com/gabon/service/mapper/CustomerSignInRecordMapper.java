package com.gabon.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gabon.service.model.entity.CustomerSignInRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户签到记录Mapper
 */
@Mapper
public interface CustomerSignInRecordMapper extends BaseMapper<CustomerSignInRecord> {
}
