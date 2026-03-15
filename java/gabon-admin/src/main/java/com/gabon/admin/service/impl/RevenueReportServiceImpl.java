package com.gabon.admin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gabon.admin.mapper.CustomerTransactionMapper;
import com.gabon.admin.model.dto.RevenueReportRequest;
import com.gabon.admin.model.dto.RevenueReportResponse;
import com.gabon.admin.service.RevenueReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 营收报表服务实现
 * Revenue Report Service Implementation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RevenueReportServiceImpl implements RevenueReportService {

    private final CustomerTransactionMapper customerTransactionMapper;

    @Override
    public IPage<RevenueReportResponse> getRevenueReport(RevenueReportRequest request) {
        log.info("Querying revenue report from {} to {}, page={}, size={}",
                request.getStartDate(), request.getEndDate(), request.getPage(), request.getSize());

        // Create pagination object
        Page<RevenueReportResponse> page = new Page<>(request.getPage(), request.getSize());

        // Query with database-level pagination
        IPage<RevenueReportResponse> resultPage = customerTransactionMapper.getRevenueStatistics(
                page,
                request.getStartDate(),
                request.getEndDate());

        return resultPage;
    }
}
