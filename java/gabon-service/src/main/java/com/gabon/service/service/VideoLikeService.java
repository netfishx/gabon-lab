package com.gabon.service.service;

/**
 * 视频点赞服务接口
 */
public interface VideoLikeService {

    /**
     * 点赞视频
     * @param videoId 视频ID
     * @param userId 用户ID（当前登录用户）
     */
    void likeVideo(Long videoId, Long userId);

    /**
     * 取消点赞视频
     * @param videoId 视频ID
     * @param userId 用户ID（当前登录用户）
     */
    void unlikeVideo(Long videoId, Long userId);
}
