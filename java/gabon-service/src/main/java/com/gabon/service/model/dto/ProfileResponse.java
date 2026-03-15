package com.gabon.service.model.dto;

import java.time.Instant;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.gabon.service.config.InstantToSecondsSerializer;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户资料响应
 */
@Data
@Schema(description = "用户资料响应")
public class ProfileResponse {

    @Schema(description = "用户ID", example = "1")
    private Long id;

    @Schema(description = "用户名", example = "zhangsan")
    private String username;

    @Schema(description = "昵称", example = "张三")
    private String name;

    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @Schema(description = "邮箱", example = "test@example.com")
    private String email;

    @Schema(description = "头像URL", example = "https://example.com/avatar.jpg")
    private String avatarUrl;

    @Schema(description = "个性签名", example = "这是我的个性签名")
    private String signature;

    @Schema(description = "VIP状态: 0=普通, 1=VIP", example = "0")
    private Integer isVip;

    @Schema(description = "钻石余额", example = "1000")
    private Long diamondBalance;

    @Schema(description = "今日新增钻石", example = "100")
    private Long todayDiamond;

    @Schema(type = "integer", description = "注册时间（时间戳秒）", example = "1739174903")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant registrationTime;

    @Schema(type = "integer", description = "最后登录时间（时间戳秒）", example = "1739174903")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant lastLoginAt;

    @Schema(description = "邀请码（8位字母+数字）", example = "A3B7K2X9")
    private String inviteCode;
}
