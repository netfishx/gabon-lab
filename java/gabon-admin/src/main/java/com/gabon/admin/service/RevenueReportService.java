package com.gabon.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gabon.admin.model.dto.RevenueReportRequest;
import com.gabon.admin.model.dto.RevenueReportResponse;

/**
 * 营收报表服务接口
 * Revenue Report Service
 */
public interface RevenueReportService {

    /**
     * 查询营收报表
     * 
     * @param request 请求参数
     * @return 营收报表分页数据
     */
    IPage<RevenueReportResponse> getRevenueReport(RevenueReportRequest request);
}
