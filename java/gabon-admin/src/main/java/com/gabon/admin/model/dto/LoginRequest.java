package com.gabon.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求DTO
 */
@Data
@Schema(description = "账户登录请求")
public class LoginRequest {

    /**
     * 账户名
     */
    @Schema(description = "账户名", example = "admin", required = true)
    @NotBlank(message = "账户名不能为空")
    private String username;

    /**
     * 密码
     */
    @Schema(description = "密码", example = "123456", required = true)
    @NotBlank(message = "密码不能为空")
    private String password;
}
