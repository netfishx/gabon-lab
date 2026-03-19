package com.gabon.admin.model.entity;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gabon.common.model.BaseDO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 客户实体类
 * Customer Entity
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("customers")
@Schema(description = "客户实体 | Customer Entity")
public class Customer extends BaseDO {

    /**
     * 账户名
     */
    @Schema(description = "账户名 | Username", example = "zhangsan")
    private String username;

    /**
     * 密码哈希值
     */
    @Schema(description = "密码哈希值 | Password Hash", hidden = true)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String passwordHash;

    /**
     * 客户名称
     */
    @Schema(description = "客户名称 | Customer Name", example = "张三")
    private String name;

    /**
     * 电话
     */
    @Schema(description = "电话 | Phone", example = "13900139001")
    private String phone;

    /**
     * VIP状态
     * 0 = non-VIP (普通用户)
     * 1 = VIP (会员)
     */
    @Schema(description = "VIP状态 | VIP Status: 0=non-VIP(普通用户), 1=VIP(会员)", example = "1")
    private Integer isVip;

    /**
     * 客户头像URL
     */
    @Schema(description = "客户头像URL | Customer Avatar URL", example = "https://example.com/avatar.jpg")
    private String avatarUrl;

    @TableField("diamond_balance")
    @Schema(description = "钻石余额 | Diamond Balance", example = "1000")
    private Long diamondBalance;

    @TableField("frozen_diamond_balance")
    @Schema(description = "冻结钻石余额 | Frozen Diamond Balance", example = "300")
    private Long frozenDiamondBalance;

    /**
     * 注册时间
     */
    @Schema(description = "注册时间 | Registration Time")
    private Instant registrationTime;

    /**
     * 最后登录时间
     */
    @Schema(description = "最后登录时间 | Last Login Time")
    private Instant lastLoginAt;
}
