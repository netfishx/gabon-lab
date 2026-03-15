package com.gabon.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gabon.admin.model.dto.VideoResponse;

/**
 * 视频服务接口
 * Video Service Interface
 */
public interface VideoService {

    /**
     * 分页查询视频列表
     *
     * @param page         页码
     * @param size         每页大小
     * @param uploaderName 上传人名称（可选，模糊查询）
     * @param status       视频状态（可选）: 0=失败, 1=等待转码, 2=转码中, 3=等待审核, 4=审核通过, 5=审核不通过
     * @param isVip        上传人VIP状态（可选）: 0=non-VIP, 1=VIP
     * @param startDate    开始日期（可选，格式: yyyy-MM-dd）
     * @param endDate      结束日期（可选，格式: yyyy-MM-dd）
     * @return 视频分页数据
     */
    IPage<VideoResponse> findVideos(int page, int size, String uploaderName, Integer status,
            Integer isVip,
            String startDate, String endDate);

    /**
     * 根据ID获取视频详情
     *
     * @param id 视频ID
     * @return 视频详情
     */
    VideoResponse getVideoById(Long id);

    /**
     * 审核视频
     *
     * @param videoId     视频ID
     * @param status      审核状态: 4=审核通过, 5=审核不通过
     * @param reviewNotes 审核备注
     * @param reviewerId  审核人ID
     */
    void reviewVideo(Long videoId, Integer status, String reviewNotes, Long reviewerId);

    /**
     * 删除视频（逻辑删除）
     *
     * @param id 视频ID
     */
    void deleteVideo(Long id);
}
