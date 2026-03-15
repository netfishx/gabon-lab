package com.gabon.admin.model.entity;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gabon.common.model.BaseDO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 广告实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("advertisement")
@Schema(description = "广告实体")
public class Advertisement extends BaseDO {

    @Schema(description = "广告名称", example = "Nike双11活动")
    private String adName;

    @Schema(description = "广告商ID", example = "1")
    private Long advertiserId;

    @Schema(description = "广告资源URL（图片或视频）", example = "https://cdn.example.com/ads/001.jpg")
    private String resourceUrl;

    @Schema(description = "素材类型：1-图片 2-视频", example = "1")
    private Integer resourceType;

    @Schema(description = "广告跳转地址，可为空", example = "https://www.nike.com/activity")
    private String jumpUrl;

    @Schema(description = "剩余投放次数", example = "1000")
    private Integer remainCount;

    @Schema(description = "广告到期时间，NULL表示永不过期")
    private Instant expireTime;

    @Schema(description = "总投放次数（统计用）", example = "1000")
    private Integer totalCount;

    @Schema(description = "状态：0-下架 1-上架", example = "1")
    private Integer status;

    @Schema(description = "备注", example = "双11限时活动")
    private String remark;
}
