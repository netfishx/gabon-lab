package com.gabon.admin.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gabon.common.model.BaseDO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 广告商实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("advertiser")
@Schema(description = "广告商实体")
public class Advertiser extends BaseDO {

    @Schema(description = "广告商名称", example = "Nike")
    private String advertiserName;

    @Schema(description = "状态：0-下架 1-上架", example = "1")
    private Integer status;

    @Schema(description = "备注", example = "长期合作广告商")
    private String remark;
}
