package com.gabon.admin.controller;

import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import com.baomidou.mybatisplus.core.metadata.IPage;

import com.gabon.admin.model.dto.ReviewVideoRequest;
import com.gabon.admin.model.dto.VideoResponse;
import com.gabon.admin.service.AuthService;
import com.gabon.admin.service.VideoService;
import com.gabon.admin.model.entity.AdminUser;

import com.gabon.common.util.JsonData;
import com.gabon.common.util.SecurityUtil;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 视频审核管理控制器
 * Video Review Management Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
@Tag(name = "视频审核管理", description = "Video Review Management APIs")
public class VideoController {

    private final VideoService videoService;
    private final AuthService authService;

    @Operation(summary = "分页查询视频列表", description = "支持按上传人、视频状态、VIP状态、上传时间进行分页查询", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "查询成功，返回视频分页数据"),
            @ApiResponse(responseCode = "401", description = "未认证或令牌无效"),
            @ApiResponse(responseCode = "403", description = "无权限访问")
    })
    @GetMapping
    public JsonData<IPage<VideoResponse>> getVideos(
            @Parameter(description = "页码，从1开始 (可选，默认1)", example = "1") @RequestParam(name = "page", defaultValue = "1") int page,

            @Parameter(description = "每页大小 (可选，默认10)", example = "10") @RequestParam(name = "size", defaultValue = "10") int size,

            @Parameter(description = "上传人名称，模糊查询 (可选)", example = "张三") @RequestParam(name = "uploaderName", required = false) String uploaderName,

            @Parameter(description = "视频状态 (可选): 不传或-1=仅显示3/4/5(排除转码流水线), 3=审核中, 4=已上架, 5=未通过 | Video status (optional): omit or -1=show 3/4/5 only, 3=UNDER_REVIEW, 4=PUBLISHED, 5=NOT_PASSED", example = "3") @RequestParam(name = "status", required = false) Integer status,

            @Parameter(description = "上传人VIP状态 (可选): 0=non-VIP, 1=VIP", example = "1") @RequestParam(name = "isVip", required = false) Integer isVip,

            @Parameter(description = "开始日期 (可选，格式: yyyy-MM-dd)", example = "2026-01-01") @RequestParam(name = "startDate", required = false) String startDate,

            @Parameter(description = "结束日期 (可选，格式: yyyy-MM-dd)", example = "2026-02-09") @RequestParam(name = "endDate", required = false) String endDate) {

        IPage<VideoResponse> videos = videoService.findVideos(page, size, uploaderName, status, isVip, startDate,
                endDate);
        return JsonData.buildSuccess(videos);
    }

    @Operation(summary = "获取视频详情", description = "根据视频ID获取详细信息", security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/{id}")
    public JsonData<VideoResponse> getVideoById(
            @Parameter(description = "视频ID", example = "1") @PathVariable Long id) {

        VideoResponse video = videoService.getVideoById(id);
        return JsonData.buildSuccess(video);
    }

    @Operation(summary = "审核视频", description = "审核视频，设置审核状态和备注（审核人从token中获取）。status: 4=审核通过, 5=审核不通过", security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/{id}/review")
    public JsonData<Void> reviewVideo(
            @Parameter(description = "视频ID", example = "1") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "审核请求信息", required = true) @Valid @RequestBody ReviewVideoRequest request,
            HttpServletRequest httpRequest) {

        // 从token中获取当前用户ID作为审核人ID
        Long reviewerId = SecurityUtil.getCurrentUserId(httpRequest, token -> {
            AdminUser user = authService.getCurrentUser(token);
            return user != null ? user.getId() : null;
        });

        Integer status = request.getStatus();
        if (status == null || (status != 4 && status != 5)) {
            throw new IllegalArgumentException("无效的审核状态: " + status + "，仅支持 "
                    + "4=审核通过(APPROVED), 5=审核不通过(REJECTED)");
        }

        videoService.reviewVideo(id, status, request.getReviewNotes(), reviewerId);
        return JsonData.buildSuccess();
    }

    @Operation(summary = "删除视频", description = "逻辑删除视频", security = @SecurityRequirement(name = "Bearer"))
    @DeleteMapping("/{id}")
    public JsonData<Void> deleteVideo(
            @Parameter(description = "视频ID", example = "1") @PathVariable Long id) {

        videoService.deleteVideo(id);
        return JsonData.buildSuccess();
    }
}
