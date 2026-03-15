package com.gabon.service.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 预签名上传URL返回VO
 */
@Data
@Schema(description = "预签名上传URL返回")
public class PresignedUploadUrlVO {

    @Schema(description = "预签名PUT URL，前端直接PUT文件到此URL")
    private String uploadUrl;

    @Schema(description = "上传完成后的公开访问URL")
    private String fileUrl;

    @Schema(description = "S3 key，用于后续确认接口")
    private String s3Key;
}
