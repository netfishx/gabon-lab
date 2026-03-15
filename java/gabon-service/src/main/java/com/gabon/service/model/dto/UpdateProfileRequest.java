package com.gabon.service.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新用户资料请求
 */
@Data
@Schema(description = "更新用户资料请求")
public class UpdateProfileRequest {

    @Size(min = 1, max = 50, message = "昵称长度必须在1-50之间")
    @Schema(description = "昵称 (可选，1-50字符) | Name (optional, 1-50 chars)", example = "新昵称")
    private String name;

    @Size(max = 500, message = "头像URL长度不能超过500")
    @Schema(description = "头像URL (可选，最大500字符) | Avatar URL (optional, max 500 chars)", example = "https://example.com/avatar.jpg")
    private String avatarUrl;

    @Size(max = 255, message = "个性签名长度不能超过255")
    @Schema(description = "个性签名 (可选，最大255字符) | Signature (optional, max 255 chars)", example = "这是我的个性签名")
    private String signature;

    @Email(message = "邮箱格式不正确")
    @Schema(description = "邮箱 (可选) | Email (optional)", example = "test@example.com")
    private String email;

    @Pattern(regexp = "^((13[0-9])|(14[0-9])|(15[0-9])|(17[0-9])|(18[0-9]))\\d{8}$", message = "手机号格式不正确")
    @Schema(description = "手机号 (可选，11位数字) | Phone (optional, 11 digits)", example = "13800138000")
    private String phone;

    @Size(min = 6, max = 20, message = "取款密码长度必须在6-20之间")
    @Schema(description = "取款密码 (可选，6-20位) | Withdrawal password (optional, 6-20 chars; empty string to clear)", example = "123456")
    private String withdrawalPassword;
}
