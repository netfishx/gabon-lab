package com.gabon.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增广告请求
 */
@Data
@Schema(description = "新增广告请求")
public class CreateAdvertisementRequest {

    @NotBlank(message = "广告名称不能为空")
    @Size(max = 100, message = "广告名称不能超过100个字符")
    @Schema(description = "广告名称", example = "Nike双11活动", requiredMode = Schema.RequiredMode.REQUIRED)
    private String adName;

    @NotNull(message = "广告商ID不能为空")
    @Schema(description = "广告商ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long advertiserId;

    @NotBlank(message = "广告资源URL不能为空")
    @Schema(description = "广告资源URL（从上传接口返回的 resourceUrl）", example = "https://cdn.example.com/ads/001.jpg", requiredMode = Schema.RequiredMode.REQUIRED)
    private String resourceUrl;

    @NotNull(message = "投放次数不能为空")
    @Min(value = 1, message = "投放次数至少为1")
    @Schema(description = "投放次数", example = "1000", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer count;

    @Schema(description = "广告跳转地址，可不传", example = "https://www.nike.com/activity")
    private String jumpUrl;

    @Schema(description = "到期日期，格式：yyyy-MM-dd，不传表示永不过期", example = "2026-12-31")
    private String expireDate;

}
