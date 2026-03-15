package com.gabon.service.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户作品列表项")
public class UserVideoListItemVO {
    @Schema(description = "视频ID", example = "1")
    private Long id;
    
    @Schema(description = "缩略图URL", example = "https://...")
    private String thumbnailUrl;
    
    @Schema(description = "视频标题", example = "视频标题")
    private String title;
    
    @Schema(description = "点赞数", example = "8900")
    private Long likeCount;
    
    @Schema(description = "时长（格式化）", example = "3:00")
    private String duration;
    
    @Schema(description = "时长（秒）", example = "180")
    private Integer durationSeconds;
}
