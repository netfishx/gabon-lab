package com.gabon.service.service;

import com.gabon.service.model.vo.UserFollowListItemVO;

import java.util.List;

/**
 * 用户关注服务接口
 */
public interface UserFollowService {

    /**
     * 关注用户
     * @param followerId 关注者用户ID（当前登录用户）
     * @param followedId 被关注用户ID
     */
    void followUser(Long followerId, Long followedId);

    /**
     * 取消关注用户
     * @param followerId 关注者用户ID（当前登录用户）
     * @param followedId 被取消关注用户ID
     */
    void unfollowUser(Long followerId, Long followedId);
    
    /**
     * 获取我关注的人列表
     * @param customerId 当前登录用户ID
     * @return 关注列表（不分页）
     */
    List<UserFollowListItemVO> getFollowingList(Long customerId);
    
    /**
     * 获取我的粉丝列表
     * @param customerId 当前登录用户ID
     * @return 粉丝列表（不分页）
     */
    List<UserFollowListItemVO> getFollowersList(Long customerId);

    /**
     * 获取他人的关注列表
     * @param userId 他人用户ID
     * @param currentUserId 当前登录用户ID（可为null，表示未登录）
     * @return 关注列表
     */
    List<UserFollowListItemVO> getUserFollowingList(Long userId, Long currentUserId);

    /**
     * 获取他人的粉丝列表
     * @param userId 他人用户ID
     * @param currentUserId 当前登录用户ID（可为null，表示未登录）
     * @return 粉丝列表
     */
    List<UserFollowListItemVO> getUserFollowersList(Long userId, Long currentUserId);
}
