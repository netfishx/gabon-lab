package com.gabon.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 视频状态枚举（统一生命周期）
 * Video Status Enum
 */
@Getter
@AllArgsConstructor
@Schema(description = "视频状态枚举 | Video Status Enum")
public enum VideoStatusEnum {

    @Schema(description = "0-失败 (Failed)")
    FAILED(0, "失败"),

    @Schema(description = "1-等待转码 (Pending Transcode)")
    PENDING_TRANSCODE(1, "等待转码"),

    @Schema(description = "2-转码中 (Transcoding)")
    TRANSCODING(2, "转码中"),

    @Schema(description = "3-等待审核 (Pending Review)")
    PENDING_REVIEW(3, "等待审核"),

    @Schema(description = "4-审核通过 (Approved)")
    APPROVED(4, "审核通过"),

    @Schema(description = "5-审核不通过 (Rejected)")
    REJECTED(5, "审核不通过");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String message;

    public static VideoStatusEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (VideoStatusEnum status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }
}
