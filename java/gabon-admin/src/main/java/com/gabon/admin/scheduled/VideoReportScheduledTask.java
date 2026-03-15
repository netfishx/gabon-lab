package com.gabon.admin.scheduled;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gabon.admin.mapper.CustomerMapper;
import com.gabon.admin.mapper.DailyVideoReportMapper;
import com.gabon.admin.model.entity.Customer;
import com.gabon.admin.model.entity.DailyVideoReport;
import com.gabon.common.constant.RedisKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

/**
 * 视频报表定时任务
 * 每天北京时间 00:30 执行，从 Redis 读取前一天各上传者的播放统计数据生成日报
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoReportScheduledTask {

    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    private final StringRedisTemplate stringRedisTemplate;
    private final DailyVideoReportMapper dailyVideoReportMapper;
    private final CustomerMapper customerMapper;

    /**
     * 聚合前一天的视频播放数据（北京时间 00:30）
     */
    @Scheduled(cron = "0 30 0 * * ?", zone = "Asia/Shanghai")
    @Transactional
    public void aggregateDailyVideoReport() {
        LocalDate yesterday = LocalDate.now(BEIJING).minusDays(1);
        String dateStr = yesterday.toString();
        log.info("Starting daily video report aggregation for date: {}", dateStr);

        try {
            // 读取昨天有播放记录的上传者ID集合
            String uploadersKey = String.format(RedisKey.PLAY_REPORT_UPLOADERS, dateStr);
            Set<String> uploaderIds = stringRedisTemplate.opsForSet().members(uploadersKey);

            if (uploaderIds == null || uploaderIds.isEmpty()) {
                log.info("No play records found in Redis for date: {}", dateStr);
                return;
            }

            log.info("Found {} uploaders with plays for date: {}", uploaderIds.size(), dateStr);

            // 删除当天已存在的数据（幂等，支持重跑）
            LambdaQueryWrapper<DailyVideoReport> deleteWrapper = new LambdaQueryWrapper<>();
            deleteWrapper.eq(DailyVideoReport::getReportDate, dateStr);
            dailyVideoReportMapper.delete(deleteWrapper);

            int insertCount = 0;
            for (String uploaderIdStr : uploaderIds) {
                Long uploaderId = Long.parseLong(uploaderIdStr);

                // 读取该上传者的点击数和有效播放数
                String clicksKey = String.format(RedisKey.PLAY_REPORT_UPLOADER_CLICKS, dateStr, uploaderId);
                String validKey = String.format(RedisKey.PLAY_REPORT_UPLOADER_VALID, dateStr, uploaderId);

                String clicksStr = stringRedisTemplate.opsForValue().get(clicksKey);
                String validStr = stringRedisTemplate.opsForValue().get(validKey);

                int clickCount = clicksStr != null ? Integer.parseInt(clicksStr) : 0;
                int validCount = validStr != null ? Integer.parseInt(validStr) : 0;

                // 查询上传者信息
                Customer customer = customerMapper.selectById(uploaderId);
                String customerName = customer != null ? customer.getName() : "未知用户";
                int isVip = (customer != null && customer.getIsVip() != null) ? customer.getIsVip() : 0;

                // 插入日报数据
                DailyVideoReport report = new DailyVideoReport();
                report.setReportDate(dateStr);
                report.setCustomerId(uploaderId);
                report.setCustomerName(customerName);
                report.setIsVip(isVip);
                report.setClickCount(clickCount);
                report.setValidCount(validCount);
                report.setSettlementAmount(0L);
                dailyVideoReportMapper.insert(report);
                insertCount++;
            }

            log.info("Daily video report completed for date: {}, inserted {} rows", dateStr, insertCount);

        } catch (Exception e) {
            log.error("Error during daily video report aggregation for date: {}", dateStr, e);
            throw e;
        }
    }
}
