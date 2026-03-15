package com.gabon.admin.controller;

import com.gabon.common.enums.BizCodeEnum;
import com.gabon.common.exception.BizException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gabon.admin.annotation.RequireRole;
import com.gabon.admin.model.dto.AdUploadUrlVO;
import com.gabon.admin.model.dto.AdvertisementResponse;
import com.gabon.admin.model.dto.AdvertiserResponse;
import com.gabon.admin.model.dto.CreateAdvertisementRequest;
import com.gabon.admin.model.entity.AdminUser;
import com.gabon.admin.service.AdService;
import com.gabon.admin.service.AuthService;
import com.gabon.common.enums.UserRole;
import com.gabon.common.util.JsonData;
import com.gabon.common.util.SecurityUtil;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/ads")
@RequiredArgsConstructor
@Tag(name = "广告管理", description = "Ad Management APIs")
public class AdController {

    private final AdService adService;
    private final AuthService authService;

    // ==================== 广告商 ====================

    @Operation(summary = "查询广告商列表", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "401", description = "未认证")
    })
    @GetMapping("/advertisers")
    @RequireRole(UserRole.ADMIN)
    public JsonData<List<AdvertiserResponse>> getAdvertisers(
            @Parameter(description = "广告商ID") @RequestParam(required = false) Long id,
            @Parameter(description = "广告商名称，模糊查询") @RequestParam(required = false) String advertiserName,
            @Parameter(description = "状态：0-下架 1-上架") @RequestParam(required = false) Integer status) {

        return JsonData.buildSuccess(adService.findAdvertisers(id, advertiserName, status));
    }

    @Operation(summary = "新增广告商", security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/advertisers")
    @RequireRole(UserRole.ADMIN)
    public JsonData<Void> createAdvertiser(
            @Parameter(description = "广告商名称", required = true) @RequestParam @NotBlank String advertiserName) {

        adService.createAdvertiser(advertiserName, null);
        return JsonData.buildSuccess();
    }

    @Operation(summary = "上下架广告商", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "操作成功"),
            @ApiResponse(responseCode = "404", description = "广告商不存在")
    })
    @PutMapping("/advertisers/{id}/status")
    @RequireRole(UserRole.ADMIN)
    public JsonData<Void> toggleAdvertiserStatus(
            @Parameter(description = "广告商ID", example = "1") @PathVariable Long id,
            @Parameter(description = "状态：0-下架 1-上架", required = true) @RequestParam @NotNull @Min(0) @Max(1) Integer status) {

        adService.toggleAdvertiserStatus(id, status);
        return JsonData.buildSuccess();
    }

    // ==================== 广告 ====================

    @Operation(summary = "获取广告素材上传URL", description = "获取S3预签名PUT URL，前端直接PUT文件到S3，仅支持图片格式（jpg/jpeg/png/gif/webp）", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "获取成功"),
            @ApiResponse(responseCode = "400", description = "文件类型不支持")
    })
    @PostMapping("/advertisements/upload-url")
    @RequireRole(UserRole.ADMIN)
    public JsonData<AdUploadUrlVO> getUploadUrl(
            @Parameter(description = "原始文件名，支持后缀：.jpg/.jpeg/.png/.gif/.webp", required = true, example = "banner.jpg") @RequestParam @NotBlank String fileName,
            HttpServletRequest httpRequest) {

        Long adminId = SecurityUtil.getCurrentUserId(httpRequest, token -> {
            AdminUser user = authService.getCurrentUser(token);
            return user != null ? user.getId() : null;
        });
        return JsonData.buildSuccess(adService.getUploadUrl(adminId, fileName));
    }

    @Operation(summary = "分页查询广告列表", security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/advertisements")
    @RequireRole(UserRole.ADMIN)
    public JsonData<IPage<AdvertisementResponse>> getAdvertisements(
            @Parameter(description = "页码，从1开始", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小", example = "10") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "广告ID") @RequestParam(required = false) Long id,
            @Parameter(description = "广告名称，模糊查询") @RequestParam(required = false) String adName,
            @Parameter(description = "广告商ID") @RequestParam(required = false) Long advertiserId,
            @Parameter(description = "广告商名称，模糊查询") @RequestParam(required = false) String advertiserName,
            @Parameter(description = "状态：0-下架 1-上架") @RequestParam(required = false) Integer status) {

        return JsonData.buildSuccess(adService.findAdvertisements(page, size, id, adName, advertiserId, advertiserName, status));
    }

    @Operation(summary = "新增广告", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "新增成功"),
            @ApiResponse(responseCode = "404", description = "广告商不存在")
    })
    @PostMapping("/advertisements")
    @RequireRole(UserRole.ADMIN)
    public JsonData<Void> createAdvertisement(@Valid @RequestBody CreateAdvertisementRequest request) {
        adService.createAdvertisement(request);
        return JsonData.buildSuccess();
    }

    @Operation(summary = "上下架广告", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "操作成功"),
            @ApiResponse(responseCode = "404", description = "广告不存在")
    })
    @PutMapping("/advertisements/{id}/status")
    @RequireRole(UserRole.ADMIN)
    public JsonData<Void> toggleAdvertisementStatus(
            @Parameter(description = "广告ID", example = "1") @PathVariable Long id,
            @Parameter(description = "状态：0-下架 1-上架", required = true) @RequestParam @NotNull @Min(0) @Max(1) Integer status) {

        adService.toggleAdvertisementStatus(id, status);
        return JsonData.buildSuccess();
    }
}
