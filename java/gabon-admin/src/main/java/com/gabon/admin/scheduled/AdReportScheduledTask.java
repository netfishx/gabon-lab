package com.gabon.admin.scheduled;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gabon.admin.mapper.AdvertiserMapper;
import com.gabon.admin.mapper.DailyAdReportMapper;
import com.gabon.admin.model.entity.Advertiser;
import com.gabon.admin.model.entity.DailyAdReport;
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
 * 广告日报定时任务
 * 每天北京时间 00:30 执行，从 Redis 读取前一天各广告商的播放统计数据生成日报
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdReportScheduledTask {

    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    private final StringRedisTemplate stringRedisTemplate;
    private final DailyAdReportMapper dailyAdReportMapper;
    private final AdvertiserMapper advertiserMapper;

    @Scheduled(cron = "0 30 0 * * ?", zone = "Asia/Shanghai")
    @Transactional
    public void aggregateDailyAdReport() {
        LocalDate yesterday = LocalDate.now(BEIJING).minusDays(1);
        String dateStr = yesterday.toString();
        log.info("Starting daily ad report aggregation for date: {}", dateStr);

        try {
            String advertisersKey = String.format(RedisKey.AD_REPORT_ADVERTISERS, dateStr);
            Set<String> advertiserIds = stringRedisTemplate.opsForSet().members(advertisersKey);

            if (advertiserIds == null || advertiserIds.isEmpty()) {
                log.info("No ad play records found in Redis for date: {}", dateStr);
                return;
            }

            // 幂等：删除当天已存在数据
            LambdaQueryWrapper<DailyAdReport> deleteWrapper = new LambdaQueryWrapper<>();
            deleteWrapper.eq(DailyAdReport::getReportDate, dateStr);
            dailyAdReportMapper.delete(deleteWrapper);

            for (String advertiserIdStr : advertiserIds) {
                Long advertiserId = Long.parseLong(advertiserIdStr);

                String playsKey = String.format(RedisKey.AD_REPORT_ADVERTISER_PLAYS, dateStr, advertiserId);
                String playsStr = stringRedisTemplate.opsForValue().get(playsKey);
                int playCount = playsStr != null ? Integer.parseInt(playsStr) : 0;

                Advertiser advertiser = advertiserMapper.selectById(advertiserId);
                String advertiserName = advertiser != null ? advertiser.getAdvertiserName() : "未知广告商";

                DailyAdReport report = new DailyAdReport();
                report.setReportDate(dateStr);
                report.setAdvertiserId(advertiserId);
                report.setAdvertiserName(advertiserName);
                report.setPlayCount(playCount);
                dailyAdReportMapper.insert(report);
            }

            log.info("Daily ad report completed for date: {}, {} advertisers", dateStr, advertiserIds.size());

        } catch (Exception e) {
            log.error("Error during daily ad report aggregation for date: {}", dateStr, e);
            throw e;
        }
    }
}
