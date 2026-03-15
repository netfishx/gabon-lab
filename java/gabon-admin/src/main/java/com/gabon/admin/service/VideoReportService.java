package com.gabon.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gabon.admin.model.dto.VideoReportRequest;
import com.gabon.admin.model.dto.VideoReportSummaryResponse;
import com.gabon.admin.model.entity.DailyVideoReport;

/**
 * 视频报表服务接口
 * Video Report Service
 */
public interface VideoReportService {

    /**
     * 分页查询每日视频报表
     * 
     * @param request 请求参数
     * @return 分页结果
     */
    IPage<DailyVideoReport> getDailyVideoReport(VideoReportRequest request);

    /**
     * 分页查询视频报表汇总
     * 
     * @param request 请求参数
     * @return 汇总报表分页数据
     */
    IPage<VideoReportSummaryResponse> getVideoReportSummary(VideoReportRequest request);
}
