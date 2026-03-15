package com.gabon.service.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 视频列表项VO
 * 用于首页和热点视频列表展示
 */
@Data
@Schema(description = "视频列表项VO")
public class VideoListItemVO {

    @Schema(description = "视频ID", example = "1")
    private Long id;

    @Schema(description = "视频缩略图URL", example = "https://example.com/thumb.jpg")
    private String thumbnailUrl;

    @Schema(description = "视频标题", example = "时尚穿搭分享")
    private String title;

    @Schema(description = "视频标签列表", example = "[\"素人\", \"人妻\"]")
    private List<String> tags;

    @Schema(description = "点赞数", example = "8920")
    private Long likeCount;

    @Schema(description = "视频时长（格式：mm:ss）", example = "1:30")
    private String duration;

    @Schema(description = "视频时长（秒）", example = "90")
    private Integer durationSeconds;

    @Schema(description = "上传者姓名", example = "张三")
    private String uploaderName;

    @Schema(description = "上传者是否为VIP: 0=否, 1=是", example = "0")
    private Integer isUploaderVip;
}
