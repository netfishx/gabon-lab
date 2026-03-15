package com.gabon.admin.model.entity;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gabon.common.model.BaseDO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理员用户实体类
 * Admin User Entity
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("admin_users")
@Schema(description = "管理员用户实体 | Admin User Entity")
public class AdminUser extends BaseDO {

    /**
     * 账户名
     */
    @Schema(description = "账户名 | Username", example = "admin")
    private String username;

    /**
     * 密码哈希值 (例如bcrypt加密)
     */
    @Schema(description = "密码哈希值 | Password Hash", hidden = true)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String passwordHash;

    /**
     * 角色
     * 1 = admin (管理员)
     * 2 = normal (普通用户)
     */
    @Schema(description = "账户角色 | User Role: 1=管理员(Admin), 2=普通用户(Normal)", example = "1")
    private Integer role;

    /**
     * 账户状态
     * 0 = disabled (禁用)
     * 1 = enabled (启用)
     */
    @Schema(description = "账户状态 | User Status: 0=禁用(Disabled), 1=启用(Enabled)", example = "1")
    private Integer status;

    /**
     * 全名
     */
    @Schema(description = "全名 | Full Name", example = "张三")
    private String fullName;

    /**
     * 电话
     */
    @Schema(description = "电话 | Phone", example = "13800138000")
    private String phone;

    /**
     * 头像URL
     */
    @Schema(description = "头像URL | Avatar URL", example = "https://example.com/avatar.jpg")
    private String avatarUrl;

    /**
     * 最后登录时间
     */
    @Schema(description = "最后登录时间 | Last Login Time")
    private Instant lastLoginAt;
}
