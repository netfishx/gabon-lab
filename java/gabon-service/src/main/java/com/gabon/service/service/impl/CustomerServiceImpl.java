package com.gabon.service.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gabon.common.enums.BizCodeEnum;
import com.gabon.common.exception.BizException;
import com.gabon.common.service.S3Service;
import com.gabon.common.util.CommonUtil;
import com.gabon.common.util.PasswordUtil;
import com.gabon.service.mapper.CustomerMapper;
import com.gabon.service.mapper.UserFollowMapper;
import com.gabon.service.mapper.VideoMapper;
import com.gabon.service.model.dto.PresignedUploadUrlRequest;
import com.gabon.service.model.dto.ProfileResponse;
import com.gabon.service.model.dto.UpdateProfileRequest;
import com.gabon.service.model.entity.Customer;
import com.gabon.service.model.entity.UserFollow;
import com.gabon.service.model.entity.Video;
import com.gabon.service.model.vo.PresignedUploadUrlVO;
import com.gabon.service.model.vo.UserProfileVO;
import com.gabon.service.model.vo.UserVideoListItemVO;
import com.gabon.service.service.CustomerService;
import com.gabon.service.service.CustomerTransactionService;
import com.gabon.service.util.VideoUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 客户服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {
    @Autowired
    CustomerMapper customerMapper;
    @Autowired
    UserFollowMapper userFollowMapper;
    @Autowired
    VideoMapper videoMapper;
    @Autowired
    S3Service s3Service;
    @Autowired
    CustomerTransactionService customerTransactionService;

    @Override
    public ProfileResponse getProfile(Long customerId) {
        // 1. 查询用户信息
        Customer customer = customerMapper.selectOne(
            new LambdaQueryWrapper<Customer>()
                .eq(Customer::getId, customerId)
                .isNull(Customer::getDeletedFlag)
        );

        if (customer == null) {
            log.warn("获取用户资料失败：用户不存在 - customerId: {}", customerId);
            throw new BizException(BizCodeEnum.ACCOUNT_UNLOGIN);
        }

        // 2. 构建响应（没有数据就返回null）
        ProfileResponse response = new ProfileResponse();
        response.setId(customer.getId());
        response.setUsername(customer.getUsername());
        response.setName(customer.getName());
        response.setPhone(customer.getPhone());
        response.setEmail(customer.getEmail()); // 可以为null
        response.setAvatarUrl(customer.getAvatarUrl()); // 可以为null
        response.setSignature(customer.getSignature()); // 可以为null
        response.setIsVip(customer.getIsVip());
        response.setDiamondBalance(customer.getDiamondBalance());
        
        // 获取今日新增钻石
        Long todayDiamond = customerTransactionService.getTodayDiamond(customerId);
        response.setTodayDiamond(todayDiamond);

        response.setRegistrationTime(customer.getRegistrationTime());
        response.setLastLoginAt(customer.getLastLoginAt());
        response.setInviteCode(customer.getInviteCode());

        return response;
    }

    @Override
    @Transactional
    public ProfileResponse updateProfile(UpdateProfileRequest request, Long customerId) {
        // 1. 查询用户信息
        Customer customer = customerMapper.selectOne(
            new LambdaQueryWrapper<Customer>()
                .eq(Customer::getId, customerId)
                .isNull(Customer::getDeletedFlag)
        );

        if (customer == null) {
            log.warn("更新用户资料失败：用户不存在 - customerId: {}", customerId);
            throw new BizException(BizCodeEnum.ACCOUNT_UNLOGIN);
        }

        // 2. 更新提供的字段（只更新非空字段）
        boolean updated = false;

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            customer.setName(request.getName().trim());
            updated = true;
        }

        if (request.getAvatarUrl() != null) {
            customer.setAvatarUrl(request.getAvatarUrl());
            updated = true;
        }

        if (request.getSignature() != null) {
            customer.setSignature(request.getSignature());
            updated = true;
        }

        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            // 验证邮箱格式（@Valid已处理，这里再次确认）
            customer.setEmail(request.getEmail().trim());
            updated = true;
        }

        // 处理手机号：如果提供了手机号，只验证格式（不检查唯一性，允许多个用户使用相同手机号）
        if (request.getPhone() != null && !request.getPhone().trim().isEmpty()) {
            String newPhone = request.getPhone().trim();
            // 验证手机号格式（@Valid已处理，这里再次确认）
            customer.setPhone(newPhone);
            updated = true;
            log.info("更新手机号 - customerId: {}, phone: {}", customerId, newPhone);
        }

        // 处理取款密码：如果提供了密码则加密存储，如果传了空字符串则清除
        if (request.getWithdrawalPassword() != null) {
            if (request.getWithdrawalPassword().trim().isEmpty()) {
                // 传空字符串表示清除取款密码
                customer.setWithdrawalPasswordHash(null);
                updated = true;
                log.info("清除取款密码 - customerId: {}", customerId);
            } else {
                // 加密取款密码
                String withdrawalPasswordHash = PasswordUtil.encryptPassword(request.getWithdrawalPassword());
                customer.setWithdrawalPasswordHash(withdrawalPasswordHash);
                updated = true;
                log.info("设置取款密码 - customerId: {}", customerId);
            }
        }
        // 如果 withdrawalPassword 为 null，表示不更新此字段

        // 3. 如果有更新，保存到数据库
        if (updated) {
            customerMapper.updateById(customer);
            log.info("更新用户资料成功 - customerId: {}, username: {}", 
                customer.getId(), customer.getUsername());
        }

        // 4. 返回更新后的完整资料
        return getProfile(customerId);
    }

    @Override
    public UserProfileVO getUserProfile(Long userId, Long currentUserId) {
        // 1. 查询用户基本信息
        Customer customer = customerMapper.selectOne(
            new LambdaQueryWrapper<Customer>()
                .eq(Customer::getId, userId)
                .isNull(Customer::getDeletedFlag)
        );
        
        if (customer == null) {
            throw new BizException(BizCodeEnum.CUSTOMER_NOT_EXIST);
        }
        
        // 2. 统计关注数和粉丝数
        Long followingCount = customerMapper.countFollowingByUserId(userId);
        Long followersCount = customerMapper.countFollowersByUserId(userId);
        
        // 3. 查询关注状态（如果已登录）
        Integer followStatus = 0; // 默认未关注
        if (currentUserId != null) {
            // 查询当前用户是否关注了该用户
            UserFollow follow = userFollowMapper.selectOne(
                new LambdaQueryWrapper<UserFollow>()
                    .eq(UserFollow::getFollowerId, currentUserId)
                    .eq(UserFollow::getFollowedId, userId)
                    .eq(UserFollow::getStatus, 1)
            );
            
            if (follow != null) {
                // 当前用户已关注该用户，检查是否相互关注
                UserFollow reverseFollow = userFollowMapper.selectOne(
                    new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, userId)
                        .eq(UserFollow::getFollowedId, currentUserId)
                        .eq(UserFollow::getStatus, 1)
                );
                
                if (reverseFollow != null) {
                    followStatus = 2; // 相互关注
                } else {
                    followStatus = 1; // 已关注
                }
            } else {
                followStatus = 0; // 未关注
            }
        }
        
        // 4. 构建VO
        UserProfileVO vo = new UserProfileVO();
        vo.setId(customer.getId());
        vo.setName(customer.getName());
        vo.setAvatarUrl(customer.getAvatarUrl());
        vo.setSignature(customer.getSignature());
        vo.setIsVip(customer.getIsVip());
        vo.setFollowingCount(followingCount != null ? followingCount : 0L);
        vo.setFollowersCount(followersCount != null ? followersCount : 0L);
        vo.setFollowStatus(followStatus);
        
        return vo;
    }

    @Override
    public List<UserVideoListItemVO> getUserVideos(Long userId) {
        // 1. 验证用户存在
        Customer customer = customerMapper.selectById(userId);
        if (customer == null || customer.getDeletedFlag() != null) {
            throw new BizException(BizCodeEnum.CUSTOMER_NOT_EXIST);
        }
        
        // 2. Mapper查询数据库（返回Video实体列表）
        List<Video> videos = videoMapper.selectUserVideosList(userId);
        
        // 3. Service实现层转换为VO（将Video实体转换为UserVideoListItemVO）
        return videos.stream()
            .map(this::convertToUserVideoVO)
            .collect(Collectors.toList());
    }

    @Override
    public PresignedUploadUrlVO getAvatarUploadUrl(Long customerId, PresignedUploadUrlRequest request) {
        // 校验文件类型
        if (!request.getContentType().startsWith("image/")) {
            throw new BizException(BizCodeEnum.FILE_UPLOAD_USER_IMG_FAIL);
        }

        // 提取扩展名
        String extension = "";
        String fileName = request.getFileName();
        if (fileName != null && fileName.contains(".")) {
            extension = fileName.substring(fileName.lastIndexOf("."));
        }

        // S3 key: avatars/{customerId}/{uuid}.{ext}
        String uuid = CommonUtil.generateUUID();
        String s3Key = String.format("avatars/%d/%s%s", customerId, uuid, extension);

        // 生成预签名URL（15分钟有效）
        String uploadUrl = s3Service.generatePresignedUploadUrl(s3Key, request.getContentType(), 15);
        String fileUrl = s3Service.buildPublicUrl(s3Key);

        PresignedUploadUrlVO vo = new PresignedUploadUrlVO();
        vo.setUploadUrl(uploadUrl);
        vo.setFileUrl(fileUrl);
        vo.setS3Key(s3Key);

        return vo;
    }

    /**
     * 将Video实体转换为UserVideoListItemVO
     * 参考VideoServiceImpl.convertToVO的实现方式
     */
    private UserVideoListItemVO convertToUserVideoVO(Video video) {
        UserVideoListItemVO vo = new UserVideoListItemVO();
        vo.setId(video.getId());
        vo.setThumbnailUrl(video.getThumbnailUrl());
        vo.setTitle(video.getTitle());
        
        // 点赞数
        vo.setLikeCount(video.getLikeCount() != null ? video.getLikeCount() : 0L);
        
        // 时长格式化（如：180秒 -> "3:00"）
        if (video.getDuration() != null) {
            vo.setDurationSeconds(video.getDuration());
            vo.setDuration(VideoUtil.formatDuration(video.getDuration()));
        } else {
            vo.setDurationSeconds(0);
            vo.setDuration("0:00");
        }
        
        return vo;
    }
}
