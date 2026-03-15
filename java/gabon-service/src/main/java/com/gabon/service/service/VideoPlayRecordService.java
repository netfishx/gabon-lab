package com.gabon.service.service;

import com.gabon.service.model.dto.PlayRecordResponse;

/**
 * 视频播放记录服务
 */
public interface VideoPlayRecordService {

    /**
     * 记录播放点击事件
     *
     * @param videoId    视频ID
     * @param customerId 客户ID（可为null，表示未登录用户）
     * @param ipAddress  用户IP地址
     * @return 播放记录响应
     */
    PlayRecordResponse recordPlayClick(Long videoId, Long customerId, String ipAddress);

    /**
     * 记录有效播放事件（15秒以上）
     *
     * @param videoId    视频ID
     * @param customerId 客户ID（可为null，表示未登录用户）
     * @param ipAddress  用户IP地址
     * @return 播放记录响应
     */
    PlayRecordResponse recordValidPlay(Long videoId, Long customerId, String ipAddress);
}
