package com.gabon.service.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gabon.common.model.BaseDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 客户签到记录实体
 * 映射到customer_sign_in_records表
 * 每个客户每天最多一条记录，用于月度累计统计和未来日历展示
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("customer_sign_in_records")
@Schema(description = "客户签到记录实体")
public class CustomerSignInRecord extends BaseDO {

    @Schema(description = "客户ID", example = "1")
    private Long customerId;

    @Schema(description = "签到日期", example = "2026-03-11")
    private LocalDate signInDate;

    @Schema(description = "月份键", example = "2026-03")
    private String periodKey;

    @Schema(description = "本次签到获得的钻石（日签+里程碑）", example = "1")
    private Integer diamondsAwarded;
}
