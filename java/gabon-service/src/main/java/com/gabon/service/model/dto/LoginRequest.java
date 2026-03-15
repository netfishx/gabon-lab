package com.gabon.service.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 客户登录请求
 */
@Data
@Schema(description = "客户登录请求")
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9]{3,16}$", message = "用户名必须为8-16位字母或数字")
    @Schema(description = "用户名(必填，3-100位字母或数字)", example = "usertest1", required = true)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9]{8,16}$", message = "密码必须为8-16位字母或数字")
    @Schema(description = "密码(必填，8-16位字母或数字)", example = "password1", required = true)
    private String password;
}
