package com.gabon.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 广告素材上传URL响应
 */
@Data
@Schema(description = "广告素材上传URL响应")
public class AdUploadUrlVO {

    @Schema(description = "预签名PUT URL，前端直接PUT文件到此URL")
    private String uploadUrl;

    @Schema(description = "上传完成后的公开访问URL，新增广告时填入 resourceUrl 字段")
    private String resourceUrl;

}
