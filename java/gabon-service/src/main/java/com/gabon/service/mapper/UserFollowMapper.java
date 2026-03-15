package com.gabon.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gabon.service.model.entity.UserFollow;
import com.gabon.service.model.vo.UserFollowListItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户关注Mapper
 * MyBatis映射器，用于操作user_follow表
 * Mapper层只负责数据库操作
 */
@Mapper
public interface UserFollowMapper extends BaseMapper<UserFollow> {

    /**
     * 根据关注者和被关注者查询关注关系
     */
    @Select("SELECT * FROM user_follow WHERE follower_id = #{followerId} AND followed_id = #{followedId}")
    UserFollow selectByFollowerAndFollowed(@Param("followerId") Long followerId, @Param("followedId") Long followedId);

    /**
     * 查询我关注的人列表（不分页）
     * 包含互相关注状态判断（1=已关注，2=相互关注）
     */
    @Select("SELECT " +
            "c.id, " +
            "c.name, " +
            "c.avatar_url AS avatarUrl, " +
            "c.is_vip AS isVip, " +
            "c.profile_signature AS signature, " +
            "uf.follow_time AS followTime, " +
            "CASE " +
            "    WHEN reverse_uf.id IS NOT NULL AND reverse_uf.status = 1 THEN 2 " +
            "    ELSE 1 " +
            "END AS followStatus " +
            "FROM user_follow uf " +
            "INNER JOIN customers c ON uf.followed_id = c.id " +
            "LEFT JOIN user_follow reverse_uf ON reverse_uf.follower_id = uf.followed_id " +
            "    AND reverse_uf.followed_id = #{currentUserId} " +
            "    AND reverse_uf.status = 1 " +
            "WHERE uf.follower_id = #{followerId} " +
            "AND uf.status = 1 " +
            "AND c.deleted_flag IS NULL " +
            "ORDER BY uf.follow_time DESC")
    List<UserFollowListItemVO> selectFollowingListAll(@Param("followerId") Long followerId, @Param("currentUserId") Long currentUserId);

    /**
     * 查询我的粉丝列表（不分页）
     * 包含关注状态判断（0=未关注，2=相互关注）
     */
    @Select("SELECT " +
            "c.id, " +
            "c.name, " +
            "c.avatar_url AS avatarUrl, " +
            "c.is_vip AS isVip, " +
            "c.profile_signature AS signature, " +
            "uf.follow_time AS followTime, " +
            "CASE " +
            "    WHEN reverse_uf.id IS NOT NULL AND reverse_uf.status = 1 THEN 2 " +
            "    ELSE 0 " +
            "END AS followStatus " +
            "FROM user_follow uf " +
            "INNER JOIN customers c ON uf.follower_id = c.id " +
            "LEFT JOIN user_follow reverse_uf ON reverse_uf.follower_id = #{currentUserId} " +
            "    AND reverse_uf.followed_id = uf.follower_id " +
            "WHERE uf.followed_id = #{followedId} " +
            "AND uf.status = 1 " +
            "AND c.deleted_flag IS NULL " +
            "ORDER BY uf.follow_time DESC")
    List<UserFollowListItemVO> selectFollowersListAll(@Param("followedId") Long followedId, @Param("currentUserId") Long currentUserId);

    /**
     * 查询他人的关注列表（不分页）
     * 包含关注状态判断（0=未关注，1=已关注，2=相互关注）
     */
    @Select("<script>" +
            "SELECT " +
            "c.id, " +
            "c.name, " +
            "c.avatar_url AS avatarUrl, " +
            "c.is_vip AS isVip, " +
            "c.profile_signature AS signature, " +
            "uf.follow_time AS followTime, " +
            "<if test='currentUserId != null'>" +
            "CASE " +
            "    WHEN current_uf.id IS NOT NULL AND current_uf.status = 1 " +
            "        AND reverse_uf.id IS NOT NULL AND reverse_uf.status = 1 THEN 2 " +
            "    WHEN current_uf.id IS NOT NULL AND current_uf.status = 1 THEN 1 " +
            "    ELSE 0 " +
            "END AS followStatus " +
            "</if>" +
            "<if test='currentUserId == null'>" +
            "0 AS followStatus " +
            "</if>" +
            "FROM user_follow uf " +
            "INNER JOIN customers c ON uf.followed_id = c.id " +
            "<if test='currentUserId != null'>" +
            "LEFT JOIN user_follow current_uf ON current_uf.follower_id = #{currentUserId} " +
            "    AND current_uf.followed_id = uf.followed_id " +
            "LEFT JOIN user_follow reverse_uf ON reverse_uf.follower_id = uf.followed_id " +
            "    AND reverse_uf.followed_id = #{currentUserId} " +
            "</if>" +
            "WHERE uf.follower_id = #{userId} " +
            "AND uf.status = 1 " +
            "AND c.deleted_flag IS NULL " +
            "ORDER BY uf.follow_time DESC" +
            "</script>")
    List<UserFollowListItemVO> selectUserFollowingListAll(@Param("userId") Long userId, @Param("currentUserId") Long currentUserId);

    /**
     * 查询他人的粉丝列表（不分页）
     * 包含关注状态判断（0=未关注，1=已关注，2=相互关注）
     */
    @Select("<script>" +
            "SELECT " +
            "c.id, " +
            "c.name, " +
            "c.avatar_url AS avatarUrl, " +
            "c.is_vip AS isVip, " +
            "c.profile_signature AS signature, " +
            "uf.follow_time AS followTime, " +
            "<if test='currentUserId != null'>" +
            "CASE " +
            "    WHEN current_uf.id IS NOT NULL AND current_uf.status = 1 " +
            "        AND reverse_uf.id IS NOT NULL AND reverse_uf.status = 1 THEN 2 " +
            "    WHEN current_uf.id IS NOT NULL AND current_uf.status = 1 THEN 1 " +
            "    ELSE 0 " +
            "END AS followStatus " +
            "</if>" +
            "<if test='currentUserId == null'>" +
            "0 AS followStatus " +
            "</if>" +
            "FROM user_follow uf " +
            "INNER JOIN customers c ON uf.follower_id = c.id " +
            "<if test='currentUserId != null'>" +
            "LEFT JOIN user_follow current_uf ON current_uf.follower_id = #{currentUserId} " +
            "    AND current_uf.followed_id = uf.follower_id " +
            "LEFT JOIN user_follow reverse_uf ON reverse_uf.follower_id = uf.follower_id " +
            "    AND reverse_uf.followed_id = #{currentUserId} " +
            "</if>" +
            "WHERE uf.followed_id = #{userId} " +
            "AND uf.status = 1 " +
            "AND c.deleted_flag IS NULL " +
            "ORDER BY uf.follow_time DESC" +
            "</script>")
    List<UserFollowListItemVO> selectUserFollowersListAll(@Param("userId") Long userId, @Param("currentUserId") Long currentUserId);
}
