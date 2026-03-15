package com.gabon.service.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.gabon.common.enums.VideoStatusEnum;
import com.gabon.common.model.BaseDO;
import com.gabon.service.config.InstantToSecondsSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * 视频实体
 * 映射到videos表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("videos")
@Schema(description = "视频实体")
public class Video extends BaseDO {

    /**
     * 上传客户ID
     * References customers.id (no FK constraint)
     */
    @Schema(description = "上传客户ID | Customer ID", example = "1")
    private Long customerId;

    /**
     * 上传人名称 (冗余字段，用于显示)
     */
    @Schema(description = "上传人名称 | Uploader Name", example = "张三")
    private String uploaderName;

    /**
     * 视频标题
     */
    @Schema(description = "视频标题 | Video Title", example = "精彩视频")
    private String title;

    /**
     * 视频标签 (逗号分隔)
     */
    @Schema(description = "视频标签(逗号分隔) | Video Tags (Comma-separated)", example = "素人,人妻")
    private String tags;

    /**
     * 上传时VIP状态 (冗余字段)
     * 0 = non-VIP
     * 1 = VIP
     */
    @Schema(description = "上传时VIP状态 | VIP Status at Upload Time: 0=non-VIP, 1=VIP", example = "1")
    private Integer isUploaderVip;

    /**
     * 文件名
     */
    @Schema(description = "文件名 | File Name", example = "video001.mp4")
    private String fileName;

    /**
     * 文件大小(字节)
     */
    @Schema(description = "文件大小(字节) | File Size in Bytes", example = "52428800")
    private Long fileSize;

    /**
     * 视频文件URL
     */
    @Schema(description = "视频文件URL | Video File URL", example = "/uploads/videos/video001.mp4")
    private String fileUrl;

    /**
     * 预览GIF URL
     */
    @Schema(description = "预览GIF URL | Preview GIF URL")
    private String previewGifUrl;

    /**
     * 缩略图URL
     */
    @Schema(description = "缩略图URL | Thumbnail URL", example = "/uploads/thumbnails/video001.jpg")
    private String thumbnailUrl;

    /**
     * MIME类型
     */
    @Schema(description = "MIME类型 | MIME Type", example = "video/mp4")
    private String mimeType;

    /**
     * 视频时长(秒)
     */
    @Schema(description = "视频时长(秒) | Duration in Seconds", example = "180")
    private Integer duration;

    /**
     * 视频宽度(像素)
     */
    @Schema(description = "视频宽度(像素) | Width in Pixels", example = "1920")
    private Integer width;

    /**
     * 视频高度(像素)
     */
    @Schema(description = "视频高度(像素) | Height in Pixels", example = "1080")
    private Integer height;

    /**
     * 存储提供商
     * 1 = local (本地)
     * 2 = s3 (亚马逊S3)
     * 3 = cdn (CDN)
     */
    @Schema(description = "存储提供商 | Storage Provider: 1=local, 2=s3, 3=cdn", example = "1")
    private Integer storageProvider;

    /**
     * 完整存储路径或存储桶信息
     */
    @Schema(description = "存储路径 | Storage Path")
    private String storagePath;

    /**
     * 视频状态
     * 0=失败, 1=等待转码, 2=转码中, 3=等待审核, 4=审核通过, 5=审核不通过
     */
    @Schema(description = "视频状态 | Video Status: 0=FAILED, 1=PENDING_TRANSCODE, 2=TRANSCODING, 3=PENDING_REVIEW, 4=APPROVED, 5=REJECTED", example = "1")
    private VideoStatusEnum status;

    /**
     * 审核人ID
     * References admin_users.id (no FK constraint)
     */
    @Schema(description = "审核人ID | Reviewer Admin User ID", example = "1")
    private Long reviewedBy;

    /**
     * 审核时间
     */
    @Schema(type = "integer", description = "审核时间（时间戳秒）| Review Timestamp", example = "1770699201")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant reviewedAt;

    /**
     * 审核备注
     */
    @Schema(description = "审核备注 | Review Notes", example = "Content approved")
    private String reviewNotes;

    /**
     * 总点击数
     */
    @Schema(description = "总点击数 | Total Clicks", example = "1000")
    private Long totalClicks;

    /**
     * 有效点击数
     */
    @Schema(description = "有效点击数 | Valid Clicks", example = "850")
    private Long validClicks;

    /**
     * 上传时间
     */
    @Schema(type = "integer", description = "上传时间（时间戳秒）| Upload Timestamp", example = "1768991400")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant uploadTime;

    /**
     * 点赞数
     */
    @Schema(description = "点赞数 | Like Count", example = "0")
    @TableField("like_count")
    private Long likeCount;

    /**
     * MediaConvert 转码任务ID
     */
    @Schema(description = "转码任务ID | Transcode Job ID")
    private String transcodeJobId;
}