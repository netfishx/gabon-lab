package com.gabon.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gabon.admin.model.dto.AdReportRequest;
import com.gabon.admin.model.entity.DailyAdReport;

/**
 * 广告日报服务接口
 */
public interface AdReportService {

    IPage<DailyAdReport> getDailyAdReport(AdReportRequest request);
}
