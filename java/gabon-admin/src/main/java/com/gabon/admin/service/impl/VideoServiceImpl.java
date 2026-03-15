package com.gabon.admin.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gabon.admin.mapper.VideoMapper;
import com.gabon.admin.model.dto.VideoResponse;
import com.gabon.admin.model.entity.Video;
import com.gabon.admin.service.VideoService;
import com.gabon.common.enums.VideoClientStatusEnum;
import com.gabon.common.enums.VideoStatusEnum;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 视频服务实现
 * Video Service Implementation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoServiceImpl implements VideoService {

    private final VideoMapper videoMapper;

    @Override
    public IPage<VideoResponse> findVideos(int page, int size, String uploaderName, Integer status,
            Integer isVip, String startDate, String endDate) {
        Page<Video> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<>();

        // Only query non-deleted records
        wrapper.isNull(Video::getDeletedFlag);

        // Filter by uploader name (fuzzy search)
        if (StringUtils.hasText(uploaderName)) {
            wrapper.like(Video::getUploaderName, uploaderName);
        }

        // Filter by status
        // 默认（不传 status 或传 -1）：只显示管理端可见的状态（所有 VideoClientStatusEnum 代码），排除转码流水线
        // 具体 status：通过 VideoClientStatusEnum 精确匹配
        if (status == null || status == -1) {
            List<Integer> adminVisibleStatuses = Stream.of(VideoClientStatusEnum.values())
                    .map(VideoClientStatusEnum::getCode)
                    .collect(Collectors.toList());
            wrapper.in(Video::getStatus, adminVisibleStatuses);
        } else {
            VideoClientStatusEnum clientStatus = VideoClientStatusEnum.getByCode(status);
            if (clientStatus != null) {
                wrapper.eq(Video::getStatus, VideoStatusEnum.getByCode(status));
            }
        }

        // Filter by uploader VIP status
        if (isVip != null) {
            wrapper.eq(Video::getIsUploaderVip, isVip);
        }

        // Filter by upload time range
        if (StringUtils.hasText(startDate)) {
            // Assume input is Beijing Time (GMT+8)
            try {
                java.time.LocalDate localDate = java.time.LocalDate.parse(startDate);
                java.time.Instant startInstant = localDate.atStartOfDay(java.time.ZoneId.of("Asia/Shanghai"))
                        .toInstant();
                wrapper.ge(Video::getUploadTime, startInstant);
            } catch (Exception e) {
                log.error("Invalid startDate format: {}", startDate, e);
            }
        }
        if (StringUtils.hasText(endDate)) {
            try {
                java.time.LocalDate localDate = java.time.LocalDate.parse(endDate);
                // End of day: 23:59:59.999999999
                java.time.Instant endInstant = localDate.atTime(java.time.LocalTime.MAX)
                        .atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant();
                wrapper.le(Video::getUploadTime, endInstant);
            } catch (Exception e) {
                log.error("Invalid endDate format: {}", endDate, e);
            }
        }

        // Order by upload time descending
        wrapper.orderByDesc(Video::getUploadTime);

        IPage<Video> pageResult = videoMapper.selectPage(pageParam, wrapper);
        return pageResult.convert(VideoResponse::fromEntity);
    }

    @Override
    public VideoResponse getVideoById(Long id) {
        Video video = findVideoEntityById(id);
        return VideoResponse.fromEntity(video);
    }

    private Video findVideoEntityById(Long id) {
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Video::getId, id)
                .isNull(Video::getDeletedFlag);
        return videoMapper.selectOne(wrapper);
    }

    @Override
    public void reviewVideo(Long videoId, Integer status, String reviewNotes, Long reviewerId) {
        Video video = new Video();
        video.setId(videoId);
        video.setStatus(VideoStatusEnum.getByCode(status));
        video.setReviewNotes(reviewNotes);
        video.setReviewedBy(reviewerId);
        video.setReviewedAt(Instant.now());
        video.setUpdateTime(Instant.now());

        videoMapper.updateById(video);
    }

    @Override
    public void deleteVideo(Long id) {
        Video video = new Video();
        video.setId(id);
        video.setDeletedFlag(Instant.now());
        video.setUpdateTime(Instant.now());

        videoMapper.updateById(video);
    }
}
