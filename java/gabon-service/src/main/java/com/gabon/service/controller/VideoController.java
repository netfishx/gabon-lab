package com.gabon.service.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gabon.common.util.JsonData;
import com.gabon.service.config.AuthInterceptor;
import com.gabon.service.model.dto.PlayRecordResponse;
import com.gabon.service.model.dto.PresignedUploadUrlRequest;
import com.gabon.service.model.dto.VideoConfirmUploadRequest;
import com.gabon.service.model.dto.VideoListRequest;
import com.gabon.service.model.entity.Customer;
import com.gabon.service.model.entity.Video;
import com.gabon.service.model.vo.PresignedUploadUrlVO;
import com.gabon.service.model.vo.UserVideoListItemVO;
import com.gabon.service.model.vo.VideoConfirmUploadVO;
import com.gabon.service.model.vo.VideoDetailVO;
import com.gabon.service.model.vo.VideoListItemVO;

import java.util.Collections;
import java.util.List;
import com.gabon.service.service.CustomerService;
import com.gabon.service.service.VideoLikeService;
import com.gabon.service.service.VideoPlayRecordService;
import com.gabon.service.service.VideoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 客户视频控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
@Tag(name = "视频管理", description = "视频上传、查询、删除等管理功能")
public class VideoController {
        @Autowired
        VideoService videoService;
        @Autowired
        VideoPlayRecordService videoPlayRecordService;
        @Autowired
        VideoLikeService videoLikeService;
        @Autowired
        CustomerService customerService;

        /**
         * 获取视频上传预签名URL
         */
        @Operation(summary = "获取视频上传URL", description = "获取S3预签名URL，前端拿到后直接PUT文件到S3", security = @SecurityRequirement(name = "Bearer"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "获取成功"),
                        @ApiResponse(responseCode = "400", description = "文件类型错误（仅支持视频）"),
                        @ApiResponse(responseCode = "401", description = "未认证或令牌无效")
        })
        @PostMapping("/upload-url")
        public JsonData<PresignedUploadUrlVO> getUploadUrl(
                        @Valid @RequestBody PresignedUploadUrlRequest request) {

                Customer customer = AuthInterceptor.threadLocal.get();
                PresignedUploadUrlVO vo = videoService.getVideoUploadUrl(customer.getId(), request);
                return JsonData.buildSuccess(vo);
        }

        /**
         * 确认视频上传完成
         */
        @Operation(summary = "确认视频上传", description = "前端上传文件到S3完成后，调用此接口保存视频元数据并触发转码", security = @SecurityRequirement(name = "Bearer"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "确认成功"),
                        @ApiResponse(responseCode = "400", description = "参数错误"),
                        @ApiResponse(responseCode = "401", description = "未认证或令牌无效")
        })
        @PostMapping("/confirm-upload")
        public JsonData<VideoConfirmUploadVO> confirmUpload(
                        @Valid @RequestBody VideoConfirmUploadRequest request) {

                Customer customer = AuthInterceptor.threadLocal.get();
                Video video = videoService.confirmVideoUpload(customer.getId(), request);

                VideoConfirmUploadVO vo = new VideoConfirmUploadVO();
                vo.setId(video.getId());
                vo.setTitle(video.getTitle());
                vo.setFileName(video.getFileName());
                vo.setFileSize(video.getFileSize());
                vo.setFileUrl(video.getFileUrl());
                vo.setPreviewGifUrl(video.getPreviewGifUrl());
                vo.setThumbnailUrl(video.getThumbnailUrl());
                vo.setStatus(video.getStatus() != null ? video.getStatus().getCode() : null);
                vo.setUploadTime(video.getUploadTime());

                return JsonData.buildSuccess(vo);
        }

        /**
         * 获取我的视频列表
         */
        @Operation(summary = "查询我的视频列表", description = "查询当前客户上传的视频列表，可按状态筛选。不返回分页，返回所有符合条件的视频。", security = @SecurityRequirement(name = "Bearer"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "查询成功"),
                        @ApiResponse(responseCode = "401", description = "未认证或令牌无效")
        })
        @GetMapping("/my")
        public JsonData<List<Video>> getMyVideos(
                        @Parameter(description = "前端视频状态（可选）: 3=审核中(IN 1,2,3), 4=已上架(=4), 5=未通过(IN 0,5) | Client video status (optional): 3=UNDER_REVIEW, 4=PUBLISHED, 5=NOT_PASSED", example = "4") @RequestParam(value = "status", required = false) Integer status) {

                // 从ThreadLocal获取当前客户
                Customer customer = AuthInterceptor.threadLocal.get();

                List<Video> videos = videoService.getMyVideos(customer.getId(), status);
                return JsonData.buildSuccess(videos);
        }

        /**
         * 获取视频详情
         */
        @Operation(summary = "获取视频详情", description = "根据视频ID获取详细信息（仅能查看自己的视频）", security = @SecurityRequirement(name = "Bearer"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "获取成功"),
                        @ApiResponse(responseCode = "401", description = "未认证或令牌无效"),
                        @ApiResponse(responseCode = "404", description = "视频不存在或无权限访问")
        })
        @GetMapping("/{id}")
        public JsonData<Video> getVideoById(
                        @Parameter(description = "视频ID (必填) | Video ID (required)", required = true, example = "1") @PathVariable Long id) {

                // 从ThreadLocal获取当前客户
                Customer customer = AuthInterceptor.threadLocal.get();

                Video video = videoService.getVideoById(id, customer.getId());
                return JsonData.buildSuccess(video);
        }

        /**
         * 删除视频
         */
        @Operation(summary = "删除视频", description = "删除自己的视频（仅待审核状态的视频可删除）", security = @SecurityRequirement(name = "Bearer"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "删除成功"),
                        @ApiResponse(responseCode = "401", description = "未认证或令牌无效"),
                        @ApiResponse(responseCode = "404", description = "视频不存在或无权限访问")
        })
        @DeleteMapping("/{id}")
        public JsonData<Void> deleteVideo(
                        @Parameter(description = "视频ID (必填) | Video ID (required)", required = true, example = "1") @PathVariable Long id) {

                // 从ThreadLocal获取当前客户
                Customer customer = AuthInterceptor.threadLocal.get();

                videoService.deleteVideo(id, customer.getId());
                return JsonData.buildSuccess();
        }

        /**
         * 记录播放点击
         */
        @Operation(summary = "记录播放点击", description = "记录播放点击事件。公开接口，可选认证（提供token记录用户ID）。", security = @SecurityRequirement(name = "Bearer"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "播放点击已记录"),
                        @ApiResponse(responseCode = "404", description = "视频不存在")
        })
        @PostMapping("/{videoId}/play-click")
        public JsonData<PlayRecordResponse> recordPlayClick(
                        @Parameter(description = "视频ID (必填) | Video ID (required)", required = true, example = "1") @PathVariable Long videoId,
                        HttpServletRequest request) {

                // 可选认证：登录用户记录customerId，游客为null
                Customer customer = AuthInterceptor.threadLocal.get();
                Long customerId = customer != null ? customer.getId() : null;

                String ipAddress = getClientIpAddress(request);

                PlayRecordResponse response = videoPlayRecordService.recordPlayClick(
                                videoId, customerId, ipAddress);
                return JsonData.buildSuccess(response);
        }

        /**
         * 记录有效播放（15秒以上）
         */
        @Operation(summary = "记录有效播放", description = "记录有效播放事件（15秒以上）。公开接口，可选认证（提供token记录用户ID并更新任务进度）。", security = @SecurityRequirement(name = "Bearer"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "有效播放已记录"),
                        @ApiResponse(responseCode = "404", description = "视频不存在")
        })
        @PostMapping("/{videoId}/play-valid")
        public JsonData<PlayRecordResponse> recordValidPlay(
                        @Parameter(description = "视频ID (必填) | Video ID (required)", required = true, example = "1") @PathVariable Long videoId,
                        HttpServletRequest request) {

                // 可选认证：登录用户记录customerId并更新任务进度，游客为null
                Customer customer = AuthInterceptor.threadLocal.get();
                Long customerId = customer != null ? customer.getId() : null;

                String ipAddress = getClientIpAddress(request);

                PlayRecordResponse response = videoPlayRecordService.recordValidPlay(
                                videoId, customerId, ipAddress);
                return JsonData.buildSuccess(response);
        }

        /**
         * 获取首页视频列表
         */
        @Operation(summary = "获取首页视频列表", description = "随机返回指定数量的视频，传入 excludeIds 可排除已看过的视频，实现无重复的无限滚动")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "查询成功")
        })
        @GetMapping
        public JsonData<List<VideoListItemVO>> getHomeVideos(
                        @Parameter(description = "每次返回数量 | Page size", example = "10") @RequestParam(value = "size", defaultValue = "10") Integer size,
                        @Parameter(description = "已看过的视频ID列表（可选，翻页时传入排除已展示视频: ?excludeIds=1&excludeIds=2）") @RequestParam(value = "excludeIds", required = false) List<Long> excludeIds) {

                VideoListRequest request = new VideoListRequest();
                request.setSize(size);
                request.setExcludeIds(excludeIds);

                return JsonData.buildSuccess(videoService.getHomeVideos(request));
        }

        /**
         * 获取热点视频列表
         */
        @Operation(summary = "获取热点视频列表", description = "分页获取所有审核通过且未删除的视频，随机排列。支持可选的关键词搜索（按视频标题模糊搜索）")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "查询成功")
        })
        @GetMapping("/featured")
        public JsonData<IPage<VideoListItemVO>> getFeaturedVideos(
                        @Parameter(description = "页码 (从1开始) | Page number (1-indexed)", example = "1") @RequestParam(value = "page", defaultValue = "1") Integer page,
                        @Parameter(description = "每页数量 | Page size", example = "10") @RequestParam(value = "size", defaultValue = "10") Integer size,
                        @Parameter(description = "搜索关键词（可选，仅搜索标题）| Search keyword in title only", example = "时尚") @RequestParam(value = "keyword", required = false) String keyword,
                        @Parameter(description = "单个标签过滤（可选）| Single tag filter", example = "素人") @RequestParam(value = "tag", required = false) String tag) {

                VideoListRequest request = new VideoListRequest();
                request.setPage(page);
                request.setSize(size);
                request.setKeyword(keyword);
                request.setTags(tag != null && !tag.trim().isEmpty() ? Collections.singletonList(tag) : null);

                return JsonData.buildSuccess(videoService.getFeaturedVideos(request));
        }

        /**
         * 获取视频详情（公开接口）
         */
        @Operation(summary = "获取视频详情", description = "获取视频详细信息，包括视频信息、作者信息。公开接口，可选认证（提供token可获取互动状态）。", security = @SecurityRequirement(name = "Bearer"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "获取成功")
        })
        @GetMapping("/{id}/detail")
        public JsonData<VideoDetailVO> getVideoDetail(
                        @Parameter(description = "视频ID (必填) | Video ID (required)", required = true, example = "1") @PathVariable Long id) {

                // 可选认证：登录用户返回互动状态，游客不返回
                Customer customer = AuthInterceptor.threadLocal.get();
                Long currentUserId = customer != null ? customer.getId() : null;

                VideoDetailVO result = videoService.getVideoDetail(id, currentUserId);
                return JsonData.buildSuccess(result);
        }

        /**
         * 点赞视频
         */
        @Operation(summary = "点赞视频", description = "对视频进行点赞操作", security = @SecurityRequirement(name = "Bearer"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "点赞成功"),
                        @ApiResponse(responseCode = "401", description = "未认证或令牌无效"),
                        @ApiResponse(responseCode = "404", description = "视频不存在或未审核通过"),
                        @ApiResponse(responseCode = "400", description = "已经点赞该视频")
        })
        @PostMapping("/{id}/like")
        public JsonData<Void> likeVideo(
                        @Parameter(description = "视频ID (必填) | Video ID (required)", required = true, example = "1") @PathVariable Long id) {

                // 从ThreadLocal获取当前客户
                Customer customer = AuthInterceptor.threadLocal.get();

                videoLikeService.likeVideo(id, customer.getId());
                return JsonData.buildSuccess();
        }

        /**
         * 取消点赞视频
         */
        @Operation(summary = "取消点赞视频", description = "取消对视频的点赞操作", security = @SecurityRequirement(name = "Bearer"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "取消点赞成功"),
                        @ApiResponse(responseCode = "401", description = "未认证或令牌无效"),
                        @ApiResponse(responseCode = "404", description = "视频不存在"),
                        @ApiResponse(responseCode = "400", description = "未点赞该视频，无法取消点赞")
        })
        @DeleteMapping("/{id}/like")
        public JsonData<Void> unlikeVideo(
                        @Parameter(description = "视频ID (必填) | Video ID (required)", required = true, example = "1") @PathVariable Long id) {

                // 从ThreadLocal获取当前客户
                Customer customer = AuthInterceptor.threadLocal.get();

                videoLikeService.unlikeVideo(id, customer.getId());
                return JsonData.buildSuccess();
        }

        /**
         * 获取他人作品列表
         */
        @Operation(summary = "获取他人作品列表", description = "获取指定用户的作品列表（审核通过且未删除的视频），按上传时间倒序排列。公开接口，可选认证（提供token可获取更多信息）。不分页，返回所有符合条件的视频。", security = @SecurityRequirement(name = "Bearer"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "获取成功"),
                        @ApiResponse(responseCode = "404", description = "用户不存在或已被删除")
        })
        @GetMapping("/user/{userId}")
        public JsonData<List<UserVideoListItemVO>> getUserVideos(
                        @Parameter(description = "用户ID (必填) | User ID (required)", required = true, example = "4") @PathVariable Long userId) {

                List<UserVideoListItemVO> result = customerService.getUserVideos(userId);
                return JsonData.buildSuccess(result);
        }

        /**
         * 从HTTP请求中提取客户端IP地址
         * 优先检查X-Forwarded-For请求头（用于代理请求）
         */
        private String getClientIpAddress(HttpServletRequest request) {
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                        // X-Forwarded-For可能包含多个IP，取第一个
                        return xForwardedFor.split(",")[0].trim();
                }
                return request.getRemoteAddr();
        }
}
