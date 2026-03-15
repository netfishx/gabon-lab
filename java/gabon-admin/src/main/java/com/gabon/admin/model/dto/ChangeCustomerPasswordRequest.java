package com.gabon.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 修改客户密码请求
 * Change Customer Password Request
 */
@Data
@Schema(description = "修改客户密码请求 | Change Customer Password Request")
public class ChangeCustomerPasswordRequest {

    @Schema(description = "客户ID | Customer ID", example = "1")
    private Long customerId;

    @Schema(description = "新密码 | New Password", example = "newPassword123")
    private String newPassword;
}
