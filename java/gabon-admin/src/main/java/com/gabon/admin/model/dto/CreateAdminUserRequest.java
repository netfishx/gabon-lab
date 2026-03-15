package com.gabon.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建管理员用户请求
 * Create Admin User Request
 */
@Data
@Schema(description = "创建管理员用户请求 | Create Admin User Request")
public class CreateAdminUserRequest {

    /**
     * 账户名
     */
    @NotBlank(message = "账户名不能为空")
    @Size(min = 3, max = 100, message = "账户名长度必须在3-100之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "账户名只能包含字母、数字和下划线")
    @Schema(description = "账户名 | Username", example = "admin123", required = true)
    private String username;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 50, message = "密码长度必须在6-50之间")
    @Schema(description = "密码 | Password (至少6位，包含字母和数字)", example = "admin123", required = true)
    private String password;

    /**
     * 角色
     * 1 = admin (管理员) - 仅用于 "admin" 用户
     * 2 = normal (普通用户) - 默认角色
     */
    @Schema(description = "角色 | Role: 1=管理员(Admin, only for 'admin' user), 2=普通用户(Normal, default)", example = "2")
    private Integer role = 2;

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
     * 账户状态
     * 0 = disabled (禁用)
     * 1 = enabled (启用)
     */
    @Schema(description = "账户状态 | Status: 0=禁用(Disabled), 1=启用(Enabled)", example = "1")
    private Integer status = 1;
}
