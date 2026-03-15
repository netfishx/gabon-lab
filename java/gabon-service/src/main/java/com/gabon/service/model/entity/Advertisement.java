package com.gabon.service.model.entity;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gabon.common.model.BaseDO;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 广告实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("advertisement")
public class Advertisement extends BaseDO {

    private String adName;
    private Long advertiserId;
    private String resourceUrl;
    private Integer resourceType;
    private String jumpUrl;
    private Integer remainCount;
    private Instant expireTime;
    private Integer totalCount;
    private Integer status;
    private String remark;
}
