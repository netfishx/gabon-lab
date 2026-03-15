package com.gabon.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求
 * Change Password Request
 */
@Data
@Schema(description = "修改密码请求 | Change Password Request")
public class ChangePasswordRequest {

    /**
     * 新密码
     */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 50, message = "新密码长度必须在6-50之间")
    @Schema(description = "新密码 | New Password (至少6位，包含字母和数字)", example = "newPassword123", required = true)
    private String newPassword;
}
