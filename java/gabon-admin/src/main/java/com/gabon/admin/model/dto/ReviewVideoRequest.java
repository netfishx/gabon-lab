package com.gabon.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 视频审核请求DTO
 * Video Review Request DTO
 */
@Data
@Schema(description = "视频审核请求")
public class ReviewVideoRequest {

    @NotNull(message = "审核状态不能为空")
    @Schema(description = "审核状态: 4=审核通过, 5=审核不通过", example = "4")
    private Integer status;

    @Schema(description = "审核备注", example = "内容合规")
    private String reviewNotes;
}
