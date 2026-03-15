package com.gabon.service.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.gabon.service.config.InstantToSecondsSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;

/**
 * 刷新令牌响应
 */
@Data
@Schema(description = "刷新令牌响应")
public class RefreshTokenResponse {

    @Schema(description = "新的JWT令牌", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String token;

    @Schema(description = "令牌类型", example = "Bearer")
    private String tokenType;

    @Schema(type = "integer", description = "令牌过期时间（时间戳秒）", example = "1739174903")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant expiresAt;
}
