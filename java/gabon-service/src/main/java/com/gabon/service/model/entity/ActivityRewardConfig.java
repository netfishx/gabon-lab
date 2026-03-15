package com.gabon.service.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gabon.common.model.BaseDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 活动奖励配置实体
 * 映射到activity_reward_config表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("activity_reward_config")
@Schema(description = "活动奖励配置实体")
public class ActivityRewardConfig extends BaseDO {

    @Schema(description = "配置类型: SIGN_IN_MILESTONE / DAILY_SIGN_IN / INVITE_REWARD", example = "SIGN_IN_MILESTONE")
    private String configType;

    @Schema(description = "配置键（里程碑=天数, 日签=daily, 邀请=invite）", example = "7")
    private String configKey;

    @Schema(description = "奖励钻石数", example = "66")
    private Integer rewardDiamonds;

    @Schema(description = "显示顺序", example = "1")
    private Integer displayOrder;

    @Schema(description = "状态: 0=禁用, 1=启用", example = "1")
    private Integer status;
}
