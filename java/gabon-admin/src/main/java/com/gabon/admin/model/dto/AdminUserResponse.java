package com.gabon.admin.model.dto;

import java.time.Instant;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.gabon.admin.config.InstantToSecondsSerializer;
import com.gabon.admin.model.entity.AdminUser;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理员用户响应
 * Admin User Response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "管理员用户响应 | Admin User Response")
public class AdminUserResponse {

    @Schema(description = "用户ID | User ID", example = "1")
    private Long id;

    @Schema(description = "账户名 | Username", example = "admin")
    private String username;

    @Schema(description = "角色 | Role: 1=管理员(Admin), 2=普通用户(Normal)", example = "1")
    private Integer role;

    @Schema(description = "账户状态 | Status: 0=禁用(Disabled), 1=启用(Enabled)", example = "1")
    private Integer status;

    @Schema(description = "全名 | Full Name", example = "张三")
    private String fullName;

    @Schema(description = "电话 | Phone", example = "13800138000")
    private String phone;

    @Schema(description = "头像URL | Avatar URL", example = "https://example.com/avatar.jpg")
    private String avatarUrl;

    @Schema(description = "最后登录时间 | Last Login Time")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant lastLoginAt;

    @Schema(description = "创建时间 | Create Time")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant createTime;

    @Schema(description = "创建人 | Created By")
    private String createBy;

    @Schema(description = "更新时间 | Update Time")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant updateTime;

    @Schema(description = "更新人 | Updated By")
    private String updateBy;

    /**
     * 从实体转换为响应DTO
     */
    public static AdminUserResponse fromEntity(AdminUser user) {
        if (user == null) {
            return null;
        }
        return AdminUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .status(user.getStatus())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .lastLoginAt(user.getLastLoginAt())
                .createTime(user.getCreateTime())
                .createBy(user.getCreateBy())
                .updateTime(user.getUpdateTime())
                .updateBy(user.getUpdateBy())
                .build();
    }
}
