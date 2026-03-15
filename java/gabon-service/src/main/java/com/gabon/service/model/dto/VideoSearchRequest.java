package com.gabon.service.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 视频搜索请求
 */
@Data
@Schema(description = "视频搜索请求")
public class VideoSearchRequest {

    @NotBlank(message = "搜索关键词不能为空")
    @Schema(description = "搜索关键词（必填）", example = "002")
    private String keyword;

    @Schema(description = "页码（从1开始）", example = "1")
    private Integer page;

    @Schema(description = "每页数量", example = "10")
    private Integer size;
}
