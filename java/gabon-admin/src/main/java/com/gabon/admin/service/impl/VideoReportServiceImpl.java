package com.gabon.admin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gabon.admin.mapper.DailyVideoReportMapper;
import com.gabon.admin.model.dto.VideoReportRequest;
import com.gabon.admin.model.dto.VideoReportSummaryResponse;
import com.gabon.admin.model.entity.DailyVideoReport;
import com.gabon.admin.service.VideoReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 视频报表服务实现
 * Video Report Service Implementation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoReportServiceImpl implements VideoReportService {

        private final DailyVideoReportMapper dailyVideoReportMapper;

        @Override
        public IPage<DailyVideoReport> getDailyVideoReport(VideoReportRequest request) {
                log.info("Querying daily video report from {} to {}, customerId={}, customerName={}",
                                request.getStartDate(), request.getEndDate(), request.getCustomerId(),
                                request.getCustomerName());

                Page<DailyVideoReport> page = new Page<>(request.getPage(), request.getSize());

                return dailyVideoReportMapper.selectDailyReports(
                                page,
                                request.getStartDate(),
                                request.getEndDate(),
                                request.getCustomerId(),
                                request.getCustomerName());
        }

        @Override
        public IPage<VideoReportSummaryResponse> getVideoReportSummary(VideoReportRequest request) {
                log.info("Querying video report summary from {} to {}, page={}, size={}",
                                request.getStartDate(), request.getEndDate(), request.getPage(), request.getSize());

                // Create pagination object
                Page<Map<String, Object>> page = new Page<>(request.getPage(), request.getSize());

                // Query with database-level pagination
                IPage<Map<String, Object>> resultPage = dailyVideoReportMapper.getSummaryStatistics(
                                page,
                                request.getStartDate(),
                                request.getEndDate());

                // Convert to response DTO
                Page<VideoReportSummaryResponse> responsePage = new Page<>(
                                resultPage.getCurrent(),
                                resultPage.getSize(),
                                resultPage.getTotal());

                responsePage.setRecords(
                                resultPage.getRecords().stream()
                                                .map(this::convertToSummaryResponse)
                                                .collect(Collectors.toList()));

                return responsePage;
        }

        /**
         * 转换数据库查询结果为汇总响应DTO
         */
        private VideoReportSummaryResponse convertToSummaryResponse(Map<String, Object> map) {
                // MyBatis returns java.sql.Date, need to convert to LocalDate then to Instant
                Object dateObj = map.get("date");
                LocalDate date;
                if (dateObj instanceof java.sql.Date) {
                        date = ((java.sql.Date) dateObj).toLocalDate();
                } else if (dateObj instanceof LocalDate) {
                        date = (LocalDate) dateObj;
                } else {
                        // Fallback: try to parse as string
                        date = LocalDate.parse(dateObj.toString());
                }

                // Convert LocalDate to Instant (midnight UTC)
                Instant dateInstant = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();

                Long totalClickCount = ((Number) map.getOrDefault("totalClickCount", 0)).longValue();
                Long totalValidCount = ((Number) map.getOrDefault("totalValidCount", 0)).longValue();
                Long totalSettlementAmount = ((Number) map.getOrDefault("totalSettlementAmount", 0)).longValue();

                return VideoReportSummaryResponse.builder()
                                .date(dateInstant)
                                .totalClickCount(totalClickCount)
                                .totalValidCount(totalValidCount)
                                .totalSettlementAmount(totalSettlementAmount)
                                .build();
        }
}
