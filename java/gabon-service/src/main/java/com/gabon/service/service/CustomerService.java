package com.gabon.service.service;

import com.gabon.service.model.dto.PresignedUploadUrlRequest;
import com.gabon.service.model.dto.ProfileResponse;
import com.gabon.service.model.dto.UpdateProfileRequest;
import com.gabon.service.model.vo.PresignedUploadUrlVO;
import com.gabon.service.model.vo.UserProfileVO;
import com.gabon.service.model.vo.UserVideoListItemVO;

import java.util.List;

/**
 * 客户服务接口
 */
public interface CustomerService {

    /**
     * 获取用户资料
     * @param customerId 客户ID
     * @return 用户资料响应
     */
    ProfileResponse getProfile(Long customerId);

    /**
     * 更新用户资料
     * @param request 更新资料请求
     * @param customerId 客户ID
     * @return 更新后的用户资料响应
     */
    ProfileResponse updateProfile(UpdateProfileRequest request, Long customerId);

    /**
     * 获取他人主页信息
     * @param userId 用户ID
     * @param currentUserId 当前登录用户ID（可为null，表示未登录）
     * @return 用户主页信息
     */
    UserProfileVO getUserProfile(Long userId, Long currentUserId);

    /**
     * 获取他人作品列表
     * @param userId 用户ID（他人用户ID）
     * @return 作品列表（不分页）
     */
    List<UserVideoListItemVO> getUserVideos(Long userId);

    /**
     * 获取头像上传预签名URL
     * @param customerId 客户ID
     * @param request 上传请求
     * @return 预签名URL信息
     */
    PresignedUploadUrlVO getAvatarUploadUrl(Long customerId, PresignedUploadUrlRequest request);
}
