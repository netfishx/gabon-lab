package com.gabon.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gabon.admin.model.entity.DailyAdReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DailyAdReportMapper extends BaseMapper<DailyAdReport> {

    @Select("<script>" +
            "SELECT * FROM daily_ad_reports " +
            "WHERE report_date BETWEEN #{startDate} AND #{endDate} " +
            "<if test='advertiserId != null'>AND advertiser_id = #{advertiserId}</if> " +
            "ORDER BY report_date DESC, advertiser_id ASC" +
            "</script>")
    Page<DailyAdReport> selectAdReports(
            Page<DailyAdReport> page,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("advertiserId") Long advertiserId);
}
