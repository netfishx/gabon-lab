package com.gabon.admin.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gabon.common.model.BaseDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 每日视频报表实体
 * Daily Video Report Entity
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("daily_video_reports")
@Schema(description = "每日视频报表实体")
public class DailyVideoReport extends BaseDO {

    @Schema(description = "报表日期(YYYY-MM-DD)", example = "2026-02-10")
    private String reportDate;

    @Schema(description = "客户ID", example = "1")
    private Long customerId;

    @Schema(description = "客户姓名", example = "张三")
    private String customerName;

    @Schema(description = "VIP状态: 0=普通, 1=VIP", example = "1")
    private Integer isVip;

    @Schema(description = "点击次数", example = "1580")
    private Integer clickCount;

    @Schema(description = "有效次数", example = "1245")
    private Integer validCount;

    @Schema(description = "应结算金额(钻石)", example = "0")
    private Long settlementAmount;
}
