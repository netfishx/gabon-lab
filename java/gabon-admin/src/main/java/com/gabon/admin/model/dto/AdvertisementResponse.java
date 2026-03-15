package com.gabon.admin.model.dto;

import java.time.Instant;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.gabon.admin.config.InstantToSecondsSerializer;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 广告响应DTO
 */
@Data
@Schema(description = "广告响应")
public class AdvertisementResponse {

    @Schema(description = "广告ID", example = "1")
    private Long id;

    @Schema(description = "广告名称", example = "Nike双11活动")
    private String adName;

    @Schema(description = "广告商ID", example = "1")
    private Long advertiserId;

    @Schema(description = "广告商名称", example = "Nike")
    private String advertiserName;

    @Schema(description = "广告资源URL", example = "https://cdn.example.com/ads/001.jpg")
    private String resourceUrl;

    @Schema(description = "广告跳转地址", example = "https://www.nike.com/activity")
    private String jumpUrl;

    @Schema(description = "剩余投放次数", example = "800")
    private Integer remainCount;

    @Schema(description = "广告到期时间（时间戳-秒），null表示永不过期")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant expireTime;

    @Schema(description = "总投放次数", example = "1000")
    private Integer totalCount;

    @Schema(description = "状态：0-下架 1-上架", example = "1")
    private Integer status;

    @Schema(description = "创建时间（时间戳-秒）")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant createTime;

    @Schema(description = "更新时间（时间戳-秒）")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant updateTime;

}
