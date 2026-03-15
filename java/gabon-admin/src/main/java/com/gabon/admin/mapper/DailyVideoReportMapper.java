package com.gabon.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gabon.admin.model.entity.DailyVideoReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.Map;

/**
 * 每日视频报表Mapper
 * Daily Video Report Mapper
 */
@Mapper
public interface DailyVideoReportMapper extends BaseMapper<DailyVideoReport> {

        /**
         * 分页查询每日视频报表
         * 
         * @param page         分页对象
         * @param startDate    开始日期
         * @param endDate      结束日期
         * @param customerId   客户ID（可选）
         * @param customerName 客户姓名（可选）
         * @return 分页结果
         */
        Page<DailyVideoReport> selectDailyReports(
                        Page<DailyVideoReport> page,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("customerId") Long customerId,
                        @Param("customerName") String customerName);

        /**
         * 分页查询视频报表汇总统计
         * 
         * @param page      分页参数
         * @param startDate 开始日期
         * @param endDate   结束日期
         * @return 汇总统计分页数据
         */
        IPage<Map<String, Object>> getSummaryStatistics(
                        Page<?> page,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);
}
