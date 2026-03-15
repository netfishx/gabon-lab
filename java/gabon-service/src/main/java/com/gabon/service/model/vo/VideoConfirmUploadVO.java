package com.gabon.service.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.gabon.service.config.InstantToSecondsSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;

/**
 * 视频上传确认响应VO
 */
@Data
@Schema(description = "视频上传确认响应")
public class VideoConfirmUploadVO {

    @Schema(description = "视频ID", example = "1")
    private Long id;

    @Schema(description = "视频标题", example = "精彩视频")
    private String title;

    @Schema(description = "文件名", example = "video001.mp4")
    private String fileName;

    @Schema(description = "文件大小(字节)", example = "52428800")
    private Long fileSize;

    @Schema(description = "视频源文件URL")
    private String fileUrl;

    @Schema(description = "预览GIF URL（转码完成后生成）")
    private String previewGifUrl;

    @Schema(description = "缩略图URL（转码完成后生成）")
    private String thumbnailUrl;

    @Schema(description = "视频状态: 0=失败, 1=等待转码, 2=转码中, 3=等待审核, 4=审核通过, 5=审核不通过", example = "2")
    private Integer status;

    @Schema(type = "integer", description = "上传时间（时间戳秒）", example = "1739174903")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant uploadTime;
}
