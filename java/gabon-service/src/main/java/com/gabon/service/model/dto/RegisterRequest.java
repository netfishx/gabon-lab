package com.gabon.service.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 客户注册请求
 */
@Data
@Schema(description = "客户注册请求")
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9]{5,15}$", message = "用户名必须以字母开头，且为6-16位字母或数字")
    @Schema(description = "用户名(必填，以字母开头，6-16位字母或数字) | Username (required, must start with a letter and be 6-16 alphanumeric)", example = "testuser1", required = true)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9]{6,16}$", message = "密码必须为6-16位字母或数字")
    @Schema(description = "密码 (必填，6-16位字母或数字) | Password (required, 6-16 alphanumeric)", example = "pass01", required = true)
    private String password;

    @NotBlank(message = "确认密码不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9]{6,16}$", message = "确认密码必须为6-16位字母或数字")
    @Schema(description = "确认密码 (必填，必须与密码一致) | Confirm Password (required, must match password)", example = "pass01", required = true)
    private String confirmPassword;

    @Schema(description = "邀请码（选填，8位字母+数字）", example = "A3B7K2X9")
    private String inviteCode;
}
