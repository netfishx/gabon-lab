package com.gabon.admin.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.gabon.admin.config.InstantToSecondsSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 登录响应DTO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "登录响应")
public class LoginResponse {

    /**
     * 访问令牌
     */
    @Schema(description = "JWT访问令牌", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String token;

    /**
     * 令牌类型
     */
    @Schema(description = "令牌类型", example = "Bearer")
    @Builder.Default
    private String tokenType = "Bearer";

    /**
     * 过期时间
     */
    @Schema(description = "令牌过期时间（时间戳秒）", example = "1739174903")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant expiresAt;

    /**
     * 账户信息
     */
    @Schema(description = "账户详细信息")
    private UserInfo userInfo;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Schema(description = "账户信息")
    public static class UserInfo {
        @Schema(description = "账户ID", example = "1")
        private Long id;

        @Schema(description = "账户名", example = "admin")
        private String username;

        @Schema(description = "全名", example = "管理员")
        private String fullName;

        @Schema(description = "手机号码", example = "13800138000")
        private String phone;

        @Schema(description = "头像URL", example = "https://example.com/avatar.jpg")
        private String avatarUrl;

        /**
         * 角色
         * 1 = admin (管理员)
         * 2 = normal (普通用户/预留)
         */
        @Schema(description = "角色: 1=管理员, 2=普通用户", example = "1")
        private Integer role;
    }
}
