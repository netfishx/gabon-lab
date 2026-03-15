package com.gabon.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gabon.admin.model.dto.RevenueReportRequest;
import com.gabon.admin.model.dto.RevenueReportResponse;
import com.gabon.admin.model.dto.VideoReportRequest;
import com.gabon.admin.model.dto.VideoReportSummaryResponse;
import com.gabon.admin.model.entity.DailyVideoReport;
import com.gabon.admin.service.RevenueReportService;
import com.gabon.admin.service.VideoReportService;
import com.gabon.common.util.JsonData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 报表控制器
 * Report Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "报表管理", description = "Report Management APIs")
public class ReportController {

        private final RevenueReportService revenueReportService;
        private final VideoReportService videoReportService;

        /**
         * 查询营收报表
         * Query Revenue Report
         */
        @GetMapping("/revenue")
        @Operation(summary = "查询营收报表", description = "根据日期范围分页查询营收统计数据")
        public JsonData<IPage<RevenueReportResponse>> getRevenueReport(
                        @Validated @ModelAttribute RevenueReportRequest request) {
                log.info("GET /api/reports/revenue - startDate: {}, endDate: {}, page: {}, size: {}",
                                request.getStartDate(), request.getEndDate(), request.getPage(), request.getSize());

                IPage<RevenueReportResponse> result = revenueReportService.getRevenueReport(request);
                return JsonData.buildSuccess(result);
        }

        /**
         * 查询每日视频报表
         * Query Daily Video Report
         */
        @GetMapping("/video/daily")
        @Operation(summary = "查询每日视频报表", description = "分页查询每日视频统计数据")
        public JsonData<IPage<DailyVideoReport>> getDailyVideoReport(
                        @Validated @ModelAttribute VideoReportRequest request) {
                log.info("GET /api/reports/video/daily - startDate: {}, endDate: {}, customerId: {}, customerName: {}",
                                request.getStartDate(), request.getEndDate(), request.getCustomerId(),
                                request.getCustomerName());

                IPage<DailyVideoReport> result = videoReportService.getDailyVideoReport(request);
                return JsonData.buildSuccess(result);
        }

        /**
         * 查询视频报表汇总
         * Query Video Report Summary
         */
        @GetMapping("/video/summary")
        @Operation(summary = "查询视频报表汇总", description = "根据日期范围分页查询视频报表汇总数据")
        public JsonData<IPage<VideoReportSummaryResponse>> getVideoReportSummary(
                        @Validated @ModelAttribute VideoReportRequest request) {
                log.info("GET /api/reports/video/summary - startDate: {}, endDate: {}, page: {}, size: {}",
                                request.getStartDate(), request.getEndDate(), request.getPage(), request.getSize());

                IPage<VideoReportSummaryResponse> result = videoReportService.getVideoReportSummary(request);
                return JsonData.buildSuccess(result);
        }
}
