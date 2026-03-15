package com.gabon.service.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 视频上传确认请求
 */
@Data
@Schema(description = "视频上传确认请求")
public class VideoConfirmUploadRequest {

    @NotBlank(message = "s3Key不能为空")
    @Schema(description = "从预签名接口返回的S3 key", example = "gabon/videos/source/123/1234567890-abc.mp4")
    private String s3Key;

    @NotBlank(message = "文件名不能为空")
    @Schema(description = "原始文件名", example = "my-video.mp4")
    private String fileName;

    @NotNull(message = "文件大小不能为空")
    @Schema(description = "文件大小(字节)", example = "52428800")
    private Long fileSize;

    @NotBlank(message = "MIME类型不能为空")
    @Schema(description = "文件MIME类型", example = "video/mp4")
    private String mimeType;

    @Schema(description = "视频标题 (可选)", example = "我的视频")
    private String title;

    @Schema(description = "视频描述 (可选)", example = "视频描述")
    private String description;

    @Schema(description = "视频标签列表 (最多3个)", example = "[\"素人\", \"人妻\"]")
    private List<String> tags;

    @Schema(description = "视频时长(秒，由客户端自动读取)", example = "90")
    private Integer duration;
}
