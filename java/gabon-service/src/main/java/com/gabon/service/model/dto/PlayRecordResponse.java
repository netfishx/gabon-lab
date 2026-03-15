package com.gabon.service.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 播放记录响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "播放记录响应")
public class PlayRecordResponse {

    @Schema(description = "记录ID", example = "12345")
    private Long recordId;

    @Schema(description = "视频ID", example = "1")
    private Long videoId;

    @Schema(description = "客户ID", example = "100")
    private Long customerId;

    @Schema(description = "播放类型: 1=点击, 2=有效播放", example = "1")
    private Integer playType;

    @Schema(description = "播放时间 (时间戳毫秒数)", example = "1738468584123")
    private Long playTime;
}
