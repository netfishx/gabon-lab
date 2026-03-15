package com.gabon.service.service.impl;

import com.gabon.common.enums.BizCodeEnum;
import com.gabon.common.exception.BizException;
import com.gabon.service.mapper.CustomerMapper;
import com.gabon.service.mapper.UserFollowMapper;
import com.gabon.service.model.entity.Customer;
import com.gabon.service.model.entity.UserFollow;
import com.gabon.service.model.vo.UserFollowListItemVO;
import com.gabon.service.service.UserFollowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 用户关注服务实现
 */
@Slf4j
@Service
public class UserFollowServiceImpl implements UserFollowService {

    @Autowired
    UserFollowMapper userFollowMapper;
    @Autowired
    CustomerMapper customerMapper;

    @Override
    @Transactional
    public void followUser(Long followerId, Long followedId) {
        // 1. 验证不能关注自己
        if (followerId.equals(followedId)) {
            throw new BizException(BizCodeEnum.FOLLOW_SELF_NOT_ALLOWED);
        }

        // 2. 验证被关注用户存在且未删除
        Customer followed = customerMapper.selectActiveById(followedId);
        if (followed == null) {
            throw new BizException(BizCodeEnum.FOLLOW_TARGET_NOT_EXIST);
        }

        // 3. 查询是否已存在关注关系
        UserFollow existingFollow = userFollowMapper.selectByFollowerAndFollowed(followerId, followedId);

        if (existingFollow != null) {
            // 如果已关注（status=1），返回重复操作错误
            if (existingFollow.getStatus() == 1) {
                throw new BizException(BizCodeEnum.FOLLOW_ALREADY_EXISTS);
            }
            // 如果已取消关注（status=0），更新status=1，更新follow_time
            existingFollow.setStatus(1);
            existingFollow.setFollowTime(Instant.now());
            userFollowMapper.updateById(existingFollow);
            log.info("用户重新关注 - Follower ID: {}, Followed ID: {}", followerId, followedId);
        } else {
            // 如果不存在记录，创建新记录（status=1）
            UserFollow newFollow = new UserFollow();
            newFollow.setFollowerId(followerId);
            newFollow.setFollowedId(followedId);
            newFollow.setStatus(1);
            newFollow.setFollowTime(Instant.now());
            userFollowMapper.insert(newFollow);
            log.info("用户关注 - Follower ID: {}, Followed ID: {}", followerId, followedId);
        }
    }

    @Override
    @Transactional
    public void unfollowUser(Long followerId, Long followedId) {
        // 1. 验证不能取消关注自己
        if (followerId.equals(followedId)) {
            throw new BizException(BizCodeEnum.FOLLOW_SELF_NOT_ALLOWED);
        }

        // 2. 验证被关注用户存在且未删除
        Customer followed = customerMapper.selectActiveById(followedId);
        if (followed == null) {
            throw new BizException(BizCodeEnum.FOLLOW_TARGET_NOT_EXIST);
        }

        // 3. 查询关注关系
        UserFollow existingFollow = userFollowMapper.selectByFollowerAndFollowed(followerId, followedId);

        if (existingFollow == null || existingFollow.getStatus() == 0) {
            // 如果未关注（记录不存在或status=0），返回未关注错误
            throw new BizException(BizCodeEnum.FOLLOW_NOT_EXISTS);
        }

        // 4. 如果已关注（status=1），更新status=0
        existingFollow.setStatus(0);
        userFollowMapper.updateById(existingFollow);
        log.info("用户取消关注 - Follower ID: {}, Followed ID: {}", followerId, followedId);
    }

    @Override
    public List<UserFollowListItemVO> getFollowingList(Long customerId) {
        // 传入当前用户ID（自己）用于判断互相关注
        return userFollowMapper.selectFollowingListAll(customerId, customerId);
    }

    @Override
    public List<UserFollowListItemVO> getFollowersList(Long customerId) {
        // 传入当前用户ID（自己）用于判断是否关注了粉丝
        return userFollowMapper.selectFollowersListAll(customerId, customerId);
    }

    @Override
    public List<UserFollowListItemVO> getUserFollowingList(Long userId, Long currentUserId) {
        // 验证用户存在
        Customer user = customerMapper.selectActiveById(userId);
        if (user == null) {
            throw new BizException(BizCodeEnum.CUSTOMER_NOT_EXIST);
        }
        
        return userFollowMapper.selectUserFollowingListAll(userId, currentUserId);
    }

    @Override
    public List<UserFollowListItemVO> getUserFollowersList(Long userId, Long currentUserId) {
        // 验证用户存在
        Customer user = customerMapper.selectActiveById(userId);
        if (user == null) {
            throw new BizException(BizCodeEnum.CUSTOMER_NOT_EXIST);
        }
        
        return userFollowMapper.selectUserFollowersListAll(userId, currentUserId);
    }
}
