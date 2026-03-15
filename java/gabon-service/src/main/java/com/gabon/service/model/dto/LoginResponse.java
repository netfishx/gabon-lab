package com.gabon.service.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.gabon.service.config.InstantToSecondsSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 客户登录响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "客户登录响应")
public class LoginResponse {

    @Schema(description = "JWT令牌", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String token;

    @Schema(description = "令牌类型", example = "Bearer")
    private String tokenType;

    @Schema(type = "integer", description = "令牌过期时间（时间戳秒）", example = "1739174903")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant expiresAt;

    @Schema(description = "客户信息")
    private CustomerInfo customerInfo;

    /**
     * 客户信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "客户信息")
    public static class CustomerInfo {
        @Schema(description = "客户ID", example = "1")
        private Long id;

        @Schema(description = "用户名", example = "customer001")
        private String username;

        @Schema(description = "客户姓名", example = "John Doe")
        private String name;

        @Schema(description = "手机号码", example = "13800138000")
        private String phone;

        @Schema(description = "VIP状态: 0=普通, 1=VIP", example = "0")
        private Integer isVip;

        @Schema(description = "头像URL", example = "https://example.com/avatar.jpg")
        private String avatarUrl;
    }
}
