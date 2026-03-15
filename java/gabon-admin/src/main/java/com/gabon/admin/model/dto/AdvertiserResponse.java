package com.gabon.admin.model.dto;

import java.time.Instant;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.gabon.admin.config.InstantToSecondsSerializer;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 广告商响应DTO
 */
@Data
@Schema(description = "广告商响应")
public class AdvertiserResponse {

    @Schema(description = "广告商ID", example = "1")
    private Long id;

    @Schema(description = "广告商名称", example = "Nike")
    private String advertiserName;

    @Schema(description = "状态：0-下架 1-上架", example = "1")
    private Integer status;

    @Schema(description = "创建时间（时间戳-秒）")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant createTime;

    @Schema(description = "更新时间（时间戳-秒）")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant updateTime;

}
