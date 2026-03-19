package com.gabon.admin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gabon.admin.mapper.DailyAdReportMapper;
import com.gabon.admin.model.dto.AdReportRequest;
import com.gabon.admin.model.entity.DailyAdReport;
import com.gabon.admin.service.AdReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdReportServiceImpl implements AdReportService {

    private final DailyAdReportMapper dailyAdReportMapper;

    @Override
    public IPage<DailyAdReport> getDailyAdReport(AdReportRequest request) {
        log.info("Querying daily ad report from {} to {}, advertiserId={}",
                request.getStartDate(), request.getEndDate(), request.getAdvertiserId());

        Page<DailyAdReport> page = new Page<>(request.getPage(), request.getSize());
        return dailyAdReportMapper.selectAdReports(
                page,
                request.getStartDate().toString(),
                request.getEndDate().toString(),
                request.getAdvertiserId());
    }
}
