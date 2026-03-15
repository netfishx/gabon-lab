package com.gabon.service.service.impl;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gabon.common.config.MediaConvertConfig;
import com.gabon.common.enums.BizCodeEnum;
import com.gabon.common.enums.StorageProvider;
import com.gabon.common.enums.VideoClientStatusEnum;
import com.gabon.common.enums.VideoStatusEnum;
import com.gabon.common.exception.BizException;
import com.gabon.common.service.MediaConvertService;
import com.gabon.common.service.S3Service;
import com.gabon.common.constant.RedisKey;
import com.gabon.common.util.CommonUtil;
import com.gabon.service.mapper.CustomerMapper;
import com.gabon.service.mapper.VideoMapper;
import com.gabon.service.model.dto.PresignedUploadUrlRequest;
import com.gabon.service.model.dto.VideoConfirmUploadRequest;
import com.gabon.service.model.dto.VideoListRequest;
import com.gabon.service.model.entity.Customer;
import com.gabon.service.mapper.UserFollowMapper;
import com.gabon.service.model.entity.UserFollow;
import com.gabon.service.model.entity.Video;
import com.gabon.service.model.vo.PresignedUploadUrlVO;
import com.gabon.service.model.vo.VideoDetailVO;
import com.gabon.service.model.vo.VideoListItemVO;
import com.gabon.service.service.VideoService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 视频服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoServiceImpl implements VideoService {
    @Autowired
    VideoMapper videoMapper;
    @Autowired
    CustomerMapper customerMapper;
    @Autowired
    UserFollowMapper userFollowMapper;
    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Autowired
    S3Service s3Service;
    @Lazy
    @Autowired
    MediaConvertService mediaConvertService;
    @Lazy
    @Autowired
    MediaConvertConfig mediaConvertConfig;

    @Override
    public PresignedUploadUrlVO getVideoUploadUrl(Long customerId, PresignedUploadUrlRequest request) {
        // 校验文件类型
        if (!request.getContentType().startsWith("video/")) {
            throw new BizException(BizCodeEnum.FILE_NOT_SUPPORT);
        }

        // 提取扩展名
        String extension = "";
        String fileName = request.getFileName();
        if (fileName != null && fileName.contains(".")) {
            extension = fileName.substring(fileName.lastIndexOf("."));
        }

        // S3 key 指向 source 路径: gabon/videos/source/{customerId}/{uuid}.{ext}
        String uuid = CommonUtil.generateUUID();
        String s3Key = String.format("%s%d/%s%s",
                mediaConvertConfig.getSourcePrefix(), customerId, uuid, extension);

        // 生成预签名URL（15分钟有效）
        String uploadUrl = s3Service.generatePresignedUploadUrl(s3Key, request.getContentType(), 15);
        String fileUrl = s3Service.buildPublicUrl(s3Key);

        PresignedUploadUrlVO vo = new PresignedUploadUrlVO();
        vo.setUploadUrl(uploadUrl);
        vo.setFileUrl(fileUrl);
        vo.setS3Key(s3Key);

        return vo;
    }

    @Override
    @Transactional
    public Video confirmVideoUpload(Long customerId, VideoConfirmUploadRequest request) {
        // 验证s3Key属于该用户
        String expectedPrefix = mediaConvertConfig.getSourcePrefix() + customerId + "/";
        if (!request.getS3Key().startsWith(expectedPrefix)) {
            throw new BizException(BizCodeEnum.OPS_REPEAT);
        }

        // 获取客户信息用于反规范化
        Customer customer = customerMapper.selectById(customerId);
        if (customer == null) {
            throw new BizException(BizCodeEnum.ACCOUNT_UNLOGIN);
        }

        // 1. 先创建视频记录（转码状态 PENDING）
        String sourceKey = request.getS3Key();
        Video video = new Video();
        video.setCustomerId(customerId);
        video.setUploaderName(customer.getName());
        video.setIsUploaderVip(customer.getIsVip());

        video.setFileName(request.getFileName());
        video.setFileSize(request.getFileSize());
        video.setFileUrl(s3Service.buildPublicUrl(sourceKey));
        video.setMimeType(request.getMimeType());
        video.setTitle(request.getTitle());
        video.setDuration(request.getDuration());

        if (request.getTags() != null && !request.getTags().isEmpty()) {
            if (request.getTags().size() > 3) {
                throw new BizException(BizCodeEnum.PARAM_ERROR);
            }
            video.setTags(String.join(",", request.getTags()));
        }

        video.setStorageProvider(StorageProvider.S3.getCode());
        video.setStoragePath(sourceKey);

        video.setStatus(VideoStatusEnum.PENDING_TRANSCODE);
        video.setUploadTime(Instant.now());

        videoMapper.insert(video);

        // 2. 再触发 MediaConvert 转码（使用 AWS 模板，输出到 preview/{customerId}/）
        String outputPrefix = String.format("%s%d/", mediaConvertConfig.getPreviewPrefix(), customerId);
        try {
            String jobId = mediaConvertService.createTranscodeJob(sourceKey, outputPrefix);
            video.setTranscodeJobId(jobId);
            video.setStatus(VideoStatusEnum.TRANSCODING);
            videoMapper.updateById(video);

            log.info("Video confirmed & transcode started - Video ID: {}, Customer ID: {}, JobId: {}",
                    video.getId(), customerId, jobId);
        } catch (Exception e) {
            video.setStatus(VideoStatusEnum.FAILED);
            videoMapper.updateById(video);
            log.error("转码任务创建失败，视频已保存 - Video ID: {}, 错误: {}", video.getId(), e.getMessage());
        }

        return video;
    }

    @Override
    public List<Video> getMyVideos(Long customerId, Integer status) {
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .eq(Video::getCustomerId, customerId)
                .isNull(Video::getDeletedFlag);

        // 使用前端状态枚举映射真实数据库状态
        // status=3 (审核中) → IN (1,2,3); status=5 (未通过) → IN (0,5); status=4 (已上架) → = 4
        if (status != null) {
            VideoClientStatusEnum clientStatus = VideoClientStatusEnum.getByCode(status);
            if (clientStatus != null) {
                wrapper.in(Video::getStatus, clientStatus.getInternalCodes());
            }
        }

        // 按上传时间倒序排列
        wrapper.orderByDesc(Video::getUploadTime);

        return videoMapper.selectList(wrapper);
    }

    @Override
    public Video getVideoById(Long id, Long customerId) {
        Video video = videoMapper.selectOne(
                new LambdaQueryWrapper<Video>()
                        .eq(Video::getId, id)
                        .eq(Video::getCustomerId, customerId)
                        .isNull(Video::getDeletedFlag));

        if (video == null) {
            throw new BizException(BizCodeEnum.OPS_REPEAT);
        }

        return video;
    }

    @Override
    @Transactional
    public void deleteVideo(Long id, Long customerId) {
        Video video = getVideoById(id, customerId);

        // 软删除
        video.setDeletedFlag(Instant.now());
        videoMapper.updateById(video);

        log.info("Video deleted - Video ID: {}, Customer ID: {}", id, customerId);
    }

    @Override
    public IPage<VideoListItemVO> getHomeVideos(VideoListRequest request) {
        // 1. 获取分页参数
        int page = request.getPage() != null ? request.getPage() : 1;
        int size = request.getSize() != null ? request.getSize() : 10;

        // 2. 处理搜索关键词（如果提供则trim，否则为null）
        String keyword = request.getKeyword() != null && !request.getKeyword().trim().isEmpty()
                ? request.getKeyword().trim()
                : null;

        // 3. 调用 Mapper 查询（动态 SQL 会根据 keyword 是否为空决定是否添加搜索条件）
        IPage<Video> videoPage = videoMapper.selectHomeVideos(new Page<>(page, size), keyword, request.getTags());

        // 4. 转换为 VO
        List<VideoListItemVO> voList = videoPage.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        // 5. 构建分页响应
        Page<VideoListItemVO> voPage = new Page<>(page, size, videoPage.getTotal());
        voPage.setRecords(voList);

        return voPage;
    }

    @Override
    public IPage<VideoListItemVO> getFeaturedVideos(VideoListRequest request) {
        // 1. 获取分页参数
        int page = request.getPage() != null ? request.getPage() : 1;
        int size = request.getSize() != null ? request.getSize() : 10;

        // 2. 处理搜索关键词（如果提供则trim，否则为null）
        String keyword = request.getKeyword() != null && !request.getKeyword().trim().isEmpty()
                ? request.getKeyword().trim()
                : null;

        // 3. 热点视频仅支持单个标签过滤，忽略空白标签
        String tag = null;
        if (request.getTags() != null) {
            for (String candidate : request.getTags()) {
                if (candidate != null && !candidate.trim().isEmpty()) {
                    tag = candidate.trim();
                    break;
                }
            }
        }

        // 4. 调用 Mapper 查询（keyword 仅搜索标题，tag 仅使用单个精确标签）
        IPage<Video> videoPage = videoMapper.selectFeaturedVideos(new Page<>(page, size), keyword, tag);

        // 5. 转换为 VO
        List<VideoListItemVO> voList = videoPage.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        // 6. 构建分页响应
        Page<VideoListItemVO> voPage = new Page<>(page, size, videoPage.getTotal());
        voPage.setRecords(voList);

        return voPage;
    }

    /**
     * 将 Video 实体转换为 VideoListItemVO
     */
    private VideoListItemVO convertToVO(Video video) {
        VideoListItemVO vo = new VideoListItemVO();
        vo.setId(video.getId());
        vo.setThumbnailUrl(video.getThumbnailUrl());

        // 标题：直接返回 title 字段，没有则返回 null
        vo.setTitle(video.getTitle());

        // 标签：逗号分隔转列表
        if (video.getTags() != null && !video.getTags().trim().isEmpty()) {
            vo.setTags(Arrays.asList(video.getTags().split(",")));
        } else {
            vo.setTags(java.util.Collections.emptyList());
        }

        // 点赞数
        vo.setLikeCount(video.getLikeCount() != null ? video.getLikeCount() : 0L);

        // 时长格式化
        if (video.getDuration() != null) {
            vo.setDurationSeconds(video.getDuration());
            vo.setDuration(formatDuration(video.getDuration()));
        } else {
            vo.setDurationSeconds(0);
            vo.setDuration("0:00");
        }

        vo.setUploaderName(video.getUploaderName());
        vo.setIsUploaderVip(video.getIsUploaderVip());

        return vo;
    }

    /**
     * 将秒数格式化为 mm:ss 格式
     */
    private String formatDuration(Integer seconds) {
        if (seconds == null || seconds < 0) {
            return "0:00";
        }
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%d:%02d", minutes, secs);
    }

    @Override
    public VideoDetailVO getVideoDetail(Long videoId, Long currentUserId) {
        // 1. 查询视频信息（根据ID查询）
        Video video = videoMapper.selectById(videoId);
        if (video == null) {
            throw new BizException(BizCodeEnum.OPS_REPEAT);
        }

        // 2. 查询作者信息（从 customers 表）
        Customer author = customerMapper.selectById(video.getCustomerId());
        if (author == null) {
            throw new BizException(BizCodeEnum.CUSTOMER_NOT_EXIST);
        }

        // 3. 查询互动状态
        Boolean isLiked = false;
        Boolean isFollowing = false;
        if (currentUserId != null) {
            UserFollow follow = userFollowMapper.selectOne(
                    new LambdaQueryWrapper<UserFollow>()
                            .eq(UserFollow::getFollowerId, currentUserId)
                            .eq(UserFollow::getFollowedId, author.getId())
                            .eq(UserFollow::getStatus, 1));
            isFollowing = follow != null;

            String likeKey = String.format(RedisKey.LIKE_VIDEO_USER, videoId, currentUserId);
            isLiked = Boolean.TRUE.equals(stringRedisTemplate.hasKey(likeKey));
        }

        // 5. 组装 VO 返回
        VideoDetailVO vo = new VideoDetailVO();

        // 视频信息
        VideoDetailVO.VideoInfo videoInfo = new VideoDetailVO.VideoInfo();
        videoInfo.setId(video.getId());
        videoInfo.setFileUrl(video.getFileUrl());
        videoInfo.setLikeCount(video.getLikeCount() != null ? video.getLikeCount() : 0L);

        // 标题：直接返回 title 字段，没有则返回 null
        videoInfo.setTitle(video.getTitle());
        videoInfo.setDescription(""); // 暂时为空，后续扩展

        // 标签：逗号分隔转列表
        if (video.getTags() != null && !video.getTags().trim().isEmpty()) {
            videoInfo.setTags(Arrays.asList(video.getTags().split(",")));
        } else {
            videoInfo.setTags(java.util.Collections.emptyList());
        }

        vo.setVideo(videoInfo);

        // 作者信息
        VideoDetailVO.AuthorInfo authorInfo = new VideoDetailVO.AuthorInfo();
        authorInfo.setId(author.getId());
        authorInfo.setName(author.getName());
        authorInfo.setAvatarUrl(author.getAvatarUrl());
        authorInfo.setIsVip(author.getIsVip());
        vo.setAuthor(authorInfo);

        // 互动状态
        VideoDetailVO.InteractionInfo interactionInfo = new VideoDetailVO.InteractionInfo();
        interactionInfo.setIsLiked(isLiked);
        interactionInfo.setIsFollowing(isFollowing);
        vo.setInteraction(interactionInfo);

        return vo;
    }
}
