package com.gabon.service.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 广告展示VO
 */
@Data
@Schema(description = "广告展示信息")
public class AdVO {

    @Schema(description = "广告ID", example = "1")
    private Long id;

    @Schema(description = "广告名称", example = "Nike双11活动")
    private String adName;

    @Schema(description = "广告资源URL", example = "https://cdn.example.com/ads/001.jpg")
    private String resourceUrl;

    @Schema(description = "广告跳转地址，可为空", example = "https://www.nike.com/activity")
    private String jumpUrl;


}
