package com.gabon.service.service;

import com.gabon.service.model.dto.PresignedUploadUrlRequest;
import com.gabon.service.model.dto.VideoConfirmUploadRequest;
import com.gabon.service.model.dto.VideoListRequest;
import com.gabon.service.model.entity.Video;
import com.gabon.service.model.vo.PresignedUploadUrlVO;
import com.gabon.service.model.vo.VideoDetailVO;
import com.gabon.service.model.vo.VideoListItemVO;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 客户视频服务
 */
public interface VideoService {

    /**
     * 获取视频上传预签名URL
     *
     * @param customerId 客户ID
     * @param request    上传请求
     * @return 预签名URL信息
     */
    PresignedUploadUrlVO getVideoUploadUrl(Long customerId, PresignedUploadUrlRequest request);

    /**
     * 确认视频上传完成
     *
     * @param customerId 客户ID
     * @param request    确认请求
     * @return 创建的视频实体
     */
    Video confirmVideoUpload(Long customerId, VideoConfirmUploadRequest request);

    /**
     * 获取客户的视频列表（按状态筛选）
     *
     * @param customerId 客户ID
     * @param status     视频状态（可选）：0=失败, 1=等待转码, 2=转码中, 3=等待审核,
     *                   4=审核通过, 5=审核不通过。如果为null，返回所有状态的视频
     * @return 视频列表（不分页，返回所有符合条件的视频）
     */
    List<Video> getMyVideos(Long customerId, Integer status);

    /**
     * 根据ID获取视频（仅限客户自己的视频）
     *
     * @param id         视频ID
     * @param customerId 客户ID
     * @return 视频实体
     */
    Video getVideoById(Long id, Long customerId);

    /**
     * 删除视频（仅限客户自己的视频）
     *
     * @param id         视频ID
     * @param customerId 客户ID
     */
    void deleteVideo(Long id, Long customerId);

    /**
     * 获取首页视频列表
     *
     * @param request 请求参数（含 size、keyword、tags、excludeIds）
     * @return 视频列表
     */
    List<VideoListItemVO> getHomeVideos(VideoListRequest request);

    /**
     * 获取热点视频列表
     *
     * @param request 分页请求参数（包含可选的搜索关键词）
     * @return 视频列表分页结果
     */
    IPage<VideoListItemVO> getFeaturedVideos(VideoListRequest request);

    /**
     * 获取视频详情（公开接口）
     *
     * @param videoId       视频ID
     * @param currentUserId 当前用户ID（可选，如果为null表示未登录）
     * @return 视频详情VO
     */
    VideoDetailVO getVideoDetail(Long videoId, Long currentUserId);


}
