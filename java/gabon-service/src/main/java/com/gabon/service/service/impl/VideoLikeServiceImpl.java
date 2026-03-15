package com.gabon.service.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gabon.common.constant.RedisKey;
import com.gabon.common.enums.BizCodeEnum;
import com.gabon.common.enums.VideoStatusEnum;
import com.gabon.common.exception.BizException;
import com.gabon.service.mapper.VideoMapper;
import com.gabon.service.model.entity.Video;
import com.gabon.service.service.VideoLikeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 视频点赞服务实现
 * 使用 Redis 去重：like:video:{videoId}:{userId}，TTL 7天
 * 点赞数记录在 videos.like_count，关系不持久化到 DB
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoLikeServiceImpl implements VideoLikeService {

    private static final long LIKE_TTL_DAYS = 7;

    private final VideoMapper videoMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void likeVideo(Long videoId, Long userId) {
        // 1. 验证视频存在且已审核通过
        Video video = videoMapper.selectOne(
                new LambdaQueryWrapper<Video>()
                        .eq(Video::getId, videoId)
                        .eq(Video::getStatus, VideoStatusEnum.APPROVED)
                        .isNull(Video::getDeletedFlag));
        if (video == null) {
            throw new BizException(BizCodeEnum.VIDEO_NOT_FOUND);
        }

        // 2. Redis 去重：setIfAbsent 返回 false 表示已点赞
        String key = String.format(RedisKey.LIKE_VIDEO_USER, videoId, userId);
        Boolean isNew = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LIKE_TTL_DAYS, TimeUnit.DAYS);
        if (!Boolean.TRUE.equals(isNew)) {
            throw new BizException(BizCodeEnum.VIDEO_ALREADY_LIKED);
        }

        // 3. 累加点赞数
        videoMapper.update(null, new LambdaUpdateWrapper<Video>()
                .eq(Video::getId, videoId)
                .setSql("like_count = like_count + 1"));

        log.info("视频点赞成功 - Video ID: {}, User ID: {}", videoId, userId);
    }

    @Override
    public void unlikeVideo(Long videoId, Long userId) {
        // 1. 验证视频存在
        Video video = videoMapper.selectOne(
                new LambdaQueryWrapper<Video>()
                        .eq(Video::getId, videoId)
                        .isNull(Video::getDeletedFlag));
        if (video == null) {
            throw new BizException(BizCodeEnum.VIDEO_NOT_FOUND);
        }

        // 2. 删除 Redis key：返回 false 表示未点赞
        String key = String.format(RedisKey.LIKE_VIDEO_USER, videoId, userId);
        Boolean deleted = stringRedisTemplate.delete(key);
        if (!Boolean.TRUE.equals(deleted)) {
            throw new BizException(BizCodeEnum.VIDEO_NOT_LIKED);
        }

        // 3. 扣减点赞数（防负数）
        videoMapper.update(null, new LambdaUpdateWrapper<Video>()
                .eq(Video::getId, videoId)
                .setSql("like_count = GREATEST(like_count - 1, 0)"));

        log.info("视频取消点赞成功 - Video ID: {}, User ID: {}", videoId, userId);
    }
}
