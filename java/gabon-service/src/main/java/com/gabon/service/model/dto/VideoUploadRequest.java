package com.gabon.service.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 视频上传请求
 * 视频上传的元数据
 */
@Data
@Schema(description = "视频上传元数据")
public class VideoUploadRequest {

    @Schema(description = "视频标题 (可选)", example = "My awesome video")
    private String title;

    @Schema(description = "视频描述 (可选)", example = "This is a description of my video")
    private String description;
}
