package com.gabon.service.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 修改密码请求
 */
@Data
@Schema(description = "修改密码请求")
public class ChangePasswordRequest {

    @NotBlank(message = "旧密码不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9]{8,16}$", message = "旧密码必须为8-16位字母或数字")
    @Schema(description = "旧密码(必填，8-16位字母或数字) | Old password (required, 8-16 alphanumeric)", example = "oldpass12")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9]{8,16}$", message = "新密码必须为8-16位字母或数字")
    @Schema(description = "新密码(必填，8-16位字母或数字) | New password (required, 8-16 alphanumeric)", example = "newpass12")
    private String newPassword;

    @NotBlank(message = "确认密码不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9]{8,16}$", message = "确认密码必须为8-16位字母或数字")
    @Schema(description = "确认新密码(必填，必须与新密码一致) | Confirm new password (required, must match new password)", example = "newpass12")
    private String confirmPassword;
}
