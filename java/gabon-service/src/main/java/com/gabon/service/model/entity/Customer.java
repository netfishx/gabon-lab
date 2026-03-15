package com.gabon.service.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.gabon.common.model.BaseDO;
import com.gabon.service.config.InstantToSecondsSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * 客户实体
 * 映射到customers表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("customers")
@Schema(description = "客户实体")
public class Customer extends BaseDO {

    @Schema(description = "用户名", example = "customer001")
    private String username;

    @Schema(description = "密码 (bcrypt)", hidden = true)
    private String passwordHash;

    @Schema(description = "客户姓名", example = "John Doe")
    private String name;

    @Schema(description = "手机号码", example = "13800138000")
    private String phone;

    @Schema(description = "VIP状态: 0=普通, 1=VIP", example = "0")
    private Integer isVip;

    @Schema(description = "钻石余额", example = "1000")
    private Long diamondBalance;

    @Schema(description = "头像URL", example = "https://example.com/avatar.jpg")
    private String avatarUrl;

    @Schema(description = "邮箱地址", example = "test@example.com")
    private String email;

    @TableField("profile_signature")
    @Schema(description = "个性签名", example = "这是我的个性签名")
    private String signature;

    @TableField("withdrawal_password_hash")
    @Schema(description = "取款密码", hidden = true)
    private String withdrawalPasswordHash;

    @Schema(description = "邀请码（8位字母+数字）", example = "A3B7K2X9")
    private String inviteCode;

    @Schema(description = "邀请人customer_id（为null表示非邀请注册）", example = "5")
    private Long invitedBy;

    @Schema(type = "integer", description = "注册时间（时间戳秒）", example = "1769749549")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant registrationTime;

    @Schema(type = "integer", description = "最后登录时间（时间戳秒）", example = "1770255652")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant lastLoginAt;
}
