package com.gabon.admin.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gabon.common.model.BaseDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 广告每日播放报表实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("daily_ad_reports")
@Schema(description = "广告每日播放报表")
public class DailyAdReport extends BaseDO {

    @Schema(description = "报表日期(YYYY-MM-DD)", example = "2026-03-16")
    private String reportDate;

    @Schema(description = "广告商ID", example = "1")
    private Long advertiserId;

    @Schema(description = "广告商名称", example = "阿里巴巴")
    private String advertiserName;

    @Schema(description = "当日广告播放次数", example = "15234")
    private Integer playCount;
}
