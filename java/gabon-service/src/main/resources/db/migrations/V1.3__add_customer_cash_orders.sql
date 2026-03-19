ALTER TABLE customers
    ADD COLUMN frozen_diamond_balance BIGINT NOT NULL DEFAULT 0 COMMENT '冻结钻石余额' AFTER diamond_balance;

CREATE TABLE customer_cash_orders
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no             VARCHAR(64)                         NOT NULL COMMENT '订单号',
    customer_id          BIGINT                              NOT NULL COMMENT '客户ID',
    customer_username    VARCHAR(100)                        NULL COMMENT '客户用户名快照',
    customer_name        VARCHAR(255)                        NULL COMMENT '客户昵称快照',
    order_type           TINYINT                             NOT NULL COMMENT '订单类型: 1=提现, 2=充值',
    status               TINYINT                             NOT NULL COMMENT '状态: 1=待审核, 2=已拒绝, 3=处理中, 4=成功, 5=失败',
    fiat_amount          DECIMAL(18, 2)                      NOT NULL COMMENT '法币金额',
    diamond_amount       BIGINT                              NOT NULL COMMENT '钻石数量',
    currency_code        VARCHAR(16)      DEFAULT 'CNY'      NOT NULL COMMENT '法币币种',
    exchange_rate        DECIMAL(18, 4)                      NOT NULL COMMENT '汇率（每1 CNY对应钻石数）',
    payment_channel      VARCHAR(50)                         NULL COMMENT '支付渠道',
    provider_name        VARCHAR(50)                         NULL COMMENT '第三方平台名称',
    provider_order_no    VARCHAR(100)                        NULL COMMENT '第三方订单号',
    provider_status      VARCHAR(50)                         NULL COMMENT '第三方状态',
    reviewed_by_admin_id BIGINT                              NULL COMMENT '审核管理员ID',
    reviewed_time        TIMESTAMP                           NULL COMMENT '审核时间',
    failure_reason       VARCHAR(255)                        NULL COMMENT '失败或拒绝原因',
    completed_time       TIMESTAMP                           NULL COMMENT '完成时间',
    external_reference   VARCHAR(100)                        NULL COMMENT '外部引用号',
    create_time          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL,
    update_time          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
    create_by            VARCHAR(100)                        NULL,
    update_by            VARCHAR(100)                        NULL,
    deleted_flag         TIMESTAMP                           NULL,
    CONSTRAINT uk_cash_order_no UNIQUE (order_no)
) COMMENT '客户资金订单表';

CREATE INDEX idx_cash_order_customer ON customer_cash_orders (customer_id);
CREATE INDEX idx_cash_order_type_status ON customer_cash_orders (order_type, status);
CREATE INDEX idx_cash_order_create_time ON customer_cash_orders (create_time);
