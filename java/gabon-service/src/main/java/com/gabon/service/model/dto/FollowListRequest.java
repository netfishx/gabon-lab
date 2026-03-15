package com.gabon.service.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
@Schema(description = "关注/粉丝列表请求")
public class FollowListRequest {

    @Schema(description = "页码（从1开始）", example = "1", defaultValue = "1")
    private Integer page = 1;

    @Schema(description = "每页数量", example = "20", defaultValue = "20")
    private Integer size = 20;
}
