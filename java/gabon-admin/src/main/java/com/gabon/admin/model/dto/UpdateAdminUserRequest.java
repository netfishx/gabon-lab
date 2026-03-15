package com.gabon.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新管理员用户请求
 * Update Admin User Request
 */
@Data
@Schema(description = "更新管理员用户请求 | Update Admin User Request")
public class UpdateAdminUserRequest {

    /**
     * 全名
     */
    @Size(max = 255, message = "全名长度不能超过255")
    @Schema(description = "全名 | Full Name", example = "张三")
    private String fullName;

    /**
     * 电话
     */
    @Size(max = 50, message = "电话长度不能超过50")
    @Schema(description = "电话 | Phone", example = "13800138000")
    private String phone;

    /**
     * 头像URL
     */
    @Size(max = 500, message = "头像URL长度不能超过500")
    @Schema(description = "头像URL | Avatar URL", example = "https://example.com/avatar.jpg")
    private String avatarUrl;

    /**
     * 角色
     * 1 = admin (管理员)
     * 2 = normal (普通用户)
     */
    @Schema(description = "角色 | Role: 1=管理员(Admin), 2=普通用户(Normal)", example = "2")
    private Integer role;

    /**
     * 账户状态
     * 0 = disabled (禁用)
     * 1 = enabled (启用)
     */
    @Schema(description = "账户状态 | Status: 0=禁用(Disabled), 1=启用(Enabled)", example = "1")
    private Integer status;
}
