CREATE TABLE daily_ad_reports
(
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    report_date     VARCHAR(10)  NOT NULL COMMENT '报表日期(YYYY-MM-DD)',
    advertiser_id   BIGINT       NOT NULL COMMENT '广告商ID',
    advertiser_name VARCHAR(100) NOT NULL COMMENT '广告商名称',
    play_count      INT          NOT NULL DEFAULT 0 COMMENT '当日广告播放次数',
    create_time     TIMESTAMP             DEFAULT CURRENT_TIMESTAMP NULL COMMENT '创建时间',
    update_time     TIMESTAMP             DEFAULT CURRENT_TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_report_date (report_date),
    KEY idx_advertiser_id (advertiser_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='广告每日播放报表';
