package com.gabon.admin.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.gabon.admin.config.InstantToSecondsSerializer;
import com.gabon.admin.model.entity.Video;
import com.gabon.common.enums.VideoStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 视频响应DTO
 * Video Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "视频响应")
public class VideoResponse {

    @Schema(description = "视频ID", example = "1")
    private Long id;

    @Schema(description = "上传客户ID", example = "1")
    private Long customerId;

    @Schema(description = "上传人名称", example = "张三")
    private String uploaderName;

    @Schema(description = "视频标题", example = "精彩视频")
    private String title;

    @Schema(description = "上传时VIP状态: 0=普通用户, 1=VIP", example = "1")
    private Integer isUploaderVip;

    @Schema(description = "文件名", example = "video001.mp4")
    private String fileName;

    @Schema(description = "文件大小(字节)", example = "52428800")
    private Long fileSize;

    @Schema(description = "视频文件URL", example = "/uploads/videos/video001.mp4")
    private String fileUrl;

    @Schema(description = "缩略图URL", example = "/uploads/thumbnails/video001.jpg")
    private String thumbnailUrl;

    @Schema(description = "MIME类型", example = "video/mp4")
    private String mimeType;

    @Schema(description = "视频时长(秒)", example = "180")
    private Integer duration;

    @Schema(description = "视频宽度(像素)", example = "1920")
    private Integer width;

    @Schema(description = "视频高度(像素)", example = "1080")
    private Integer height;

    @Schema(description = "存储提供商: 1=local, 2=s3, 3=cdn", example = "1")
    private Integer storageProvider;

    @Schema(description = "存储路径")
    private String storagePath;

    @Schema(description = "视频状态: 0=失败, 1=等待转码, 2=转码中, 3=等待审核, 4=审核通过, 5=审核不通过", example = "3")
    private VideoStatusEnum status;

    @Schema(description = "审核人ID", example = "1")
    private Long reviewedBy;

    @Schema(description = "审核时间(时间戳-秒)", example = "1770699201")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant reviewedAt;

    @Schema(description = "审核备注", example = "Content approved")
    private String reviewNotes;

    @Schema(description = "总点击数", example = "1000")
    private Long totalClicks;

    @Schema(description = "有效点击数", example = "850")
    private Long validClicks;

    @Schema(description = "上传时间(时间戳-秒)", example = "1768991400")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant uploadTime;

    @Schema(description = "创建时间(时间戳-秒)", example = "1769749549")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant createTime;

    @Schema(description = "更新时间(时间戳-秒)", example = "1770699201")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant updateTime;

    /**
     * 从实体转换为响应DTO
     */
    public static VideoResponse fromEntity(Video video) {
        if (video == null) {
            return null;
        }
        return VideoResponse.builder()
                .id(video.getId())
                .customerId(video.getCustomerId())
                .uploaderName(video.getUploaderName())
                .title(video.getTitle())
                .isUploaderVip(video.getIsUploaderVip())
                .fileName(video.getFileName())
                .fileSize(video.getFileSize())
                .fileUrl(video.getFileUrl())
                .thumbnailUrl(video.getThumbnailUrl())
                .mimeType(video.getMimeType())
                .duration(video.getDuration())
                .width(video.getWidth())
                .height(video.getHeight())
                .storageProvider(video.getStorageProvider())
                .storagePath(video.getStoragePath())
                .status(video.getStatus())
                .reviewedBy(video.getReviewedBy())
                .reviewedAt(video.getReviewedAt())
                .reviewNotes(video.getReviewNotes())
                .totalClicks(video.getTotalClicks())
                .validClicks(video.getValidClicks())
                .uploadTime(video.getUploadTime())
                .createTime(video.getCreateTime())
                .updateTime(video.getUpdateTime())
                .build();
    }
}
