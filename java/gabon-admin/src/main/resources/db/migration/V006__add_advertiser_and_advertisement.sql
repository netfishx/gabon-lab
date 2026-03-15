1-- Migration: Add advertiser and advertisement tables

CREATE TABLE advertiser
(
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    advertiser_name VARCHAR(100) NOT NULL COMMENT '广告商名称',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0-下架 1-上架',
    remark          VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    create_time     TIMESTAMP             DEFAULT CURRENT_TIMESTAMP NULL COMMENT '创建时间',
    create_by       VARCHAR(100)          NULL COMMENT '创建人',
    update_time     TIMESTAMP             DEFAULT CURRENT_TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    update_by       VARCHAR(100)          NULL COMMENT '修改人',
    deleted_flag    TIMESTAMP             NULL COMMENT '逻辑删除标识（时间戳）NULL表示可用，有值表示已删除',
    PRIMARY KEY (id),
    KEY idx_status (status),
    KEY idx_deleted_flag (deleted_flag)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='广告商表';

CREATE TABLE advertisement
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    ad_name       VARCHAR(100) NOT NULL COMMENT '广告名称',
    advertiser_id BIGINT       NOT NULL COMMENT '广告商ID',
    resource_url  VARCHAR(500) NOT NULL COMMENT '广告资源URL（图片或视频）',
    resource_type TINYINT      NOT NULL DEFAULT 1 COMMENT '素材类型：1-图片 2-视频',
    jump_url      VARCHAR(500)          DEFAULT NULL COMMENT '广告跳转地址，可为空',
    remain_count  INT          NOT NULL DEFAULT 0 COMMENT '剩余投放次数',
    expire_time   TIMESTAMP             DEFAULT NULL COMMENT '广告到期时间，NULL表示永不过期',
    total_count   INT          NOT NULL DEFAULT 0 COMMENT '总投放次数（统计用）',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0-下架 1-上架',
    remark        VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    create_time   TIMESTAMP             DEFAULT CURRENT_TIMESTAMP NULL COMMENT '创建时间',
    create_by     VARCHAR(100)          NULL COMMENT '创建人',
    update_time   TIMESTAMP             DEFAULT CURRENT_TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    update_by     VARCHAR(100)          NULL COMMENT '修改人',
    deleted_flag  TIMESTAMP             NULL COMMENT '逻辑删除标识（时间戳）NULL表示可用，有值表示已删除',
    PRIMARY KEY (id),
    KEY idx_advertiser_id (advertiser_id),
    KEY idx_status (status),
    KEY idx_remain_count (remain_count),
    KEY idx_deleted_flag (deleted_flag)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='广告表';
