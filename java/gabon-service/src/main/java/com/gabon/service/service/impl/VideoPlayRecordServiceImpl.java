package com.gabon.service.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gabon.common.constant.RedisKey;
import com.gabon.common.enums.BizCodeEnum;
import com.gabon.common.exception.BizException;
import com.gabon.service.mapper.VideoMapper;
import com.gabon.service.model.dto.PlayRecordResponse;
import com.gabon.service.model.entity.Video;
import com.gabon.service.service.VideoPlayRecordService;
import com.gabon.service.service.TaskProgressService;

import lombok.extern.slf4j.Slf4j;

/**
 * 视频播放记录服务实现
 * 使用 Redis 去重：同一 IP 对同一视频 24h 内只计一次
 */
@Slf4j
@Service
public class VideoPlayRecordServiceImpl implements VideoPlayRecordService {

    private static final long DEDUP_TTL_HOURS = 24;
    private static final long REPORT_TTL_HOURS = 48;
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    @Autowired
    private TaskProgressService taskProgressService;
    @Autowired
    private VideoMapper videoMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public PlayRecordResponse recordPlayClick(Long videoId, Long customerId, String ipAddress) {
        // 验证视频存在
        Video video = videoMapper.selectOne(
                new LambdaQueryWrapper<Video>()
                        .eq(Video::getId, videoId)
                        .isNull(Video::getDeletedFlag));

        if (video == null) {
            log.warn("Video not found - Video ID: {}", videoId);
            throw new BizException(BizCodeEnum.OPS_REPEAT);
        }

        // Redis 去重：同一 IP 对同一视频 24h 内只计一次
        String dedupeKey = String.format(RedisKey.PLAY_DEDUP_CLICK, videoId, ipAddress);
        Boolean isNew = stringRedisTemplate.opsForValue()
                .setIfAbsent(dedupeKey, "1", DEDUP_TTL_HOURS, TimeUnit.HOURS);

        if (Boolean.TRUE.equals(isNew)) {
            // 更新视频累计点击数
            videoMapper.update(null, new LambdaUpdateWrapper<Video>()
                    .eq(Video::getId, videoId)
                    .setSql("total_clicks = total_clicks + 1"));

            // 日报计数：按上传者维度（北京时间）
            Long uploaderId = video.getCustomerId();
            String date = LocalDate.now(BEIJING).toString();
            String clicksKey = String.format(RedisKey.PLAY_REPORT_UPLOADER_CLICKS, date, uploaderId);
            String uploadersKey = String.format(RedisKey.PLAY_REPORT_UPLOADERS, date);
            stringRedisTemplate.opsForValue().increment(clicksKey);
            stringRedisTemplate.expire(clicksKey, REPORT_TTL_HOURS, TimeUnit.HOURS);
            stringRedisTemplate.opsForSet().add(uploadersKey, String.valueOf(uploaderId));
            stringRedisTemplate.expire(uploadersKey, REPORT_TTL_HOURS, TimeUnit.HOURS);

            log.info("Play click counted - Video ID: {}, Uploader ID: {}, IP: {}",
                    videoId, uploaderId, ipAddress);
        } else {
            log.debug("Play click deduplicated - Video ID: {}, IP: {}", videoId, ipAddress);
        }

        return PlayRecordResponse.builder()
                .videoId(videoId)
                .customerId(customerId)
                .playType(1)
                .playTime(Instant.now().getEpochSecond())
                .build();
    }

    @Override
    public PlayRecordResponse recordValidPlay(Long videoId, Long customerId, String ipAddress) {
        // 验证视频存在
        Video video = videoMapper.selectOne(
                new LambdaQueryWrapper<Video>()
                        .eq(Video::getId, videoId)
                        .isNull(Video::getDeletedFlag));

        if (video == null) {
            log.warn("Video not found - Video ID: {}", videoId);
            throw new BizException(BizCodeEnum.OPS_REPEAT);
        }

        // Redis 去重：同一 IP 对同一视频 24h 内只计一次
        String dedupeKey = String.format(RedisKey.PLAY_DEDUP_VALID, videoId, ipAddress);
        Boolean isNew = stringRedisTemplate.opsForValue()
                .setIfAbsent(dedupeKey, "1", DEDUP_TTL_HOURS, TimeUnit.HOURS);

        if (Boolean.TRUE.equals(isNew)) {
            // 更新视频累计有效播放数
            videoMapper.update(null, new LambdaUpdateWrapper<Video>()
                    .eq(Video::getId, videoId)
                    .setSql("valid_clicks = valid_clicks + 1"));

            // 日报计数：按上传者维度（北京时间）
            Long uploaderId = video.getCustomerId();
            String date = LocalDate.now(BEIJING).toString();
            String validKey = String.format(RedisKey.PLAY_REPORT_UPLOADER_VALID, date, uploaderId);
            String uploadersKey = String.format(RedisKey.PLAY_REPORT_UPLOADERS, date);
            stringRedisTemplate.opsForValue().increment(validKey);
            stringRedisTemplate.expire(validKey, REPORT_TTL_HOURS, TimeUnit.HOURS);
            stringRedisTemplate.opsForSet().add(uploadersKey, String.valueOf(uploaderId));
            stringRedisTemplate.expire(uploadersKey, REPORT_TTL_HOURS, TimeUnit.HOURS);

            // 任务进度更新（仅登录用户）
            if (customerId != null) {
                try {
                    taskProgressService.updateWatchVideoProgress(customerId, videoId);
                } catch (Exception e) {
                    log.error("Failed to update task progress for customer: {}, video: {}",
                            customerId, videoId, e);
                }
            }

            log.info("Valid play counted - Video ID: {}, Customer ID: {}, IP: {}",
                    videoId, customerId, ipAddress);
        } else {
            log.debug("Valid play deduplicated - Video ID: {}, IP: {}", videoId, ipAddress);
        }

        return PlayRecordResponse.builder()
                .videoId(videoId)
                .customerId(customerId)
                .playType(2)
                .playTime(Instant.now().getEpochSecond())
                .build();
    }
}
