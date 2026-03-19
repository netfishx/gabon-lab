-- customer_sign_in_records
CREATE TABLE customer_sign_in_records (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     BIGINT NOT NULL REFERENCES customers(id),
    period_key      VARCHAR(50) NOT NULL,
    reward_diamonds INT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(customer_id, period_key)
);
CREATE INDEX idx_sign_in_records_customer ON customer_sign_in_records(customer_id);
