package com.gabon.service.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户作品列表请求")
public class UserVideoListRequest {
    @Schema(description = "页码（从1开始）", example = "1", defaultValue = "1")
    private Integer page = 1;
    
    @Schema(description = "每页数量", example = "10", defaultValue = "10")
    private Integer size = 10;
}
