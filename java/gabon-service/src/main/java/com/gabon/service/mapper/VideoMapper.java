package com.gabon.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.gabon.service.model.entity.Video;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 视频Mapper
 * MyBatis映射器，用于操作videos表
 */
@Mapper
public interface VideoMapper extends BaseMapper<Video> {

        /** 查询首页视频（无排除列表） */
        @Select("SELECT * FROM videos WHERE status = 4 AND deleted_flag IS NULL ORDER BY RAND() LIMIT #{size}")
        List<Video> selectHomeVideos(@Param("size") int size);

        /** 查询首页视频（排除已看ID） */
        @Select("<script>" +
                        "SELECT * FROM videos " +
                        "WHERE status = 4 " +
                        "AND deleted_flag IS NULL " +
                        "AND id NOT IN " +
                        "<foreach collection='excludeIds' item='eid' open='(' separator=',' close=')'>" +
                        "#{eid}" +
                        "</foreach>" +
                        "ORDER BY RAND() LIMIT #{size}" +
                        "</script>")
        List<Video> selectHomeVideosWithExclude(@Param("excludeIds") List<Long> excludeIds,
                        @Param("size") int size);

        /**
         * 查询热点视频（审核通过且未删除，随机排序）
         * 支持可选的关键词搜索（仅按标题模糊搜索）
         * 支持可选的单个标签过滤
         *
         * @param page    分页参数
         * @param keyword 搜索关键词（可选）
         * @param tag     单个标签过滤（可选）
         * @return 视频分页结果
         */
        @Select("<script>" +
                        "SELECT * FROM videos " +
                        "WHERE status = 4 " +
                        "AND deleted_flag IS NULL " +
                        "<if test='keyword != null and keyword != \"\"'>" +
                        "AND title LIKE CONCAT('%', #{keyword}, '%') " +
                        "</if>" +
                        "<if test='tag != null and tag != \"\"'>" +
                        "AND FIND_IN_SET(#{tag}, tags) &gt; 0 " +
                        "</if>" +
                        "ORDER BY RAND()" +
                        "</script>")
        IPage<Video> selectFeaturedVideos(Page<Video> page, @Param("keyword") String keyword,
                        @Param("tag") String tag);

        /**
         * 查询指定用户的作品列表（审核通过且未删除，按上传时间倒序）
         * 只负责数据库操作，不包含业务逻辑
         * 
         * @param page   分页参数
         * @param userId 用户ID
         * @return 视频分页结果
         */
        @Select("SELECT * FROM videos " +
                        "WHERE customer_id = #{userId} " +
                        "AND status = 4 " +
                        "AND deleted_flag IS NULL " +
                        "ORDER BY upload_time DESC")
        IPage<Video> selectUserVideos(Page<Video> page, @Param("userId") Long userId);

        /**
         * 查询指定用户的作品列表（不分页）
         * 
         * @param userId 用户ID
         * @return 视频列表
         */
        @Select("SELECT * FROM videos " +
                        "WHERE customer_id = #{userId} " +
                        "AND status = 4 " +
                        "AND deleted_flag IS NULL " +
                        "ORDER BY upload_time DESC")
        List<Video> selectUserVideosList(@Param("userId") Long userId);
}
