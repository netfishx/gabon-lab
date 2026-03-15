package com.gabon.service.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 获取预签名上传URL请求
 */
@Data
@Schema(description = "获取预签名上传URL请求")
public class PresignedUploadUrlRequest {

    @NotBlank(message = "文件名不能为空")
    @Schema(description = "原始文件名", example = "my-video.mp4")
    private String fileName;

    @NotBlank(message = "文件类型不能为空")
    @Schema(description = "文件MIME类型", example = "video/mp4")
    private String contentType;
}
