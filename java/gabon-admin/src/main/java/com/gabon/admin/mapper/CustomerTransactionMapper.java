package com.gabon.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gabon.admin.model.entity.CustomerTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import com.gabon.admin.model.dto.RevenueReportResponse;

/**
 * 客户交易记录Mapper
 * Customer Transaction Mapper
 */
@Mapper
public interface CustomerTransactionMapper extends BaseMapper<CustomerTransaction> {

    /**
     * 分页查询日期范围内的营收统计
     * 
     * @param page      分页参数
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 每日营收统计分页数据
     */
    IPage<RevenueReportResponse> getRevenueStatistics(Page<?> page, @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
