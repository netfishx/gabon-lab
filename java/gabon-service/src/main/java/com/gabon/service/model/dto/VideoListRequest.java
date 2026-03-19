package com.gabon.service.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 视频列表请求
 * 用于首页和热点视频列表查询
 */
@Data
@Schema(description = "视频列表请求")
public class VideoListRequest {

    @Schema(description = "页码（从1开始）", example = "1")
    private Integer page;

    @Schema(description = "每页数量", example = "10")
    private Integer size;

    @Schema(description = "搜索关键词（可选，按视频标题模糊搜索）", example = "时尚")
    private String keyword;

    @Schema(description = "过滤标签列表（可选，按多个标签过滤，OR逻辑）", example = "[\"素人\",\"人妻\"]")
    private List<String> tags;

    @Schema(description = "已看过的视频ID列表（可选，翻页时传入，排除已展示过的视频）", example = "[5, 12, 33]")
    private List<Long> excludeIds;
}
