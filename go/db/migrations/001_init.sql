-- +goose Up

-- admin_users
CREATE TABLE admin_users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(100) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    role            SMALLINT NOT NULL DEFAULT 2,
    full_name       VARCHAR(255),
    phone           VARCHAR(50),
    avatar_url      VARCHAR(500),
    status          SMALLINT NOT NULL DEFAULT 1,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);
CREATE UNIQUE INDEX idx_admin_users_username_active
    ON admin_users(LOWER(username)) WHERE deleted_at IS NULL;

-- customers
CREATE TABLE customers (
    id                          BIGSERIAL PRIMARY KEY,
    username                    VARCHAR(100) NOT NULL,
    password_hash               VARCHAR(255) NOT NULL,
    name                        VARCHAR(255),
    phone                       VARCHAR(50),
    email                       VARCHAR(255),
    avatar_url                  VARCHAR(500),
    signature                   VARCHAR(255),
    is_vip                      BOOLEAN NOT NULL DEFAULT FALSE,
    diamond_balance             BIGINT NOT NULL DEFAULT 0,
    withdrawal_password_hash    VARCHAR(255),
    last_login_at               TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at                  TIMESTAMPTZ
);
CREATE UNIQUE INDEX idx_customers_username_active
    ON customers(LOWER(username)) WHERE deleted_at IS NULL;

-- videos
CREATE TABLE videos (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     BIGINT NOT NULL REFERENCES customers(id),
    title           VARCHAR(500),
    description     TEXT,
    file_name       VARCHAR(255) NOT NULL,
    file_size       BIGINT NOT NULL,
    file_url        VARCHAR(500) NOT NULL,
    thumbnail_url   VARCHAR(500),
    preview_gif_url VARCHAR(500),
    mime_type       VARCHAR(100) NOT NULL,
    duration        INT,
    width           INT,
    height          INT,
    status          SMALLINT NOT NULL DEFAULT 1,
    review_notes    TEXT,
    reviewed_by     BIGINT REFERENCES admin_users(id),
    reviewed_at     TIMESTAMPTZ,
    total_clicks    BIGINT NOT NULL DEFAULT 0,
    valid_clicks    BIGINT NOT NULL DEFAULT 0,
    like_count      BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);
CREATE INDEX idx_videos_customer_id ON videos(customer_id);
CREATE INDEX idx_videos_status ON videos(status) WHERE deleted_at IS NULL;

-- video_play_records
CREATE TABLE video_play_records (
    id              BIGSERIAL PRIMARY KEY,
    video_id        BIGINT NOT NULL REFERENCES videos(id),
    customer_id     BIGINT REFERENCES customers(id),
    play_type       SMALLINT NOT NULL,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_play_records_video ON video_play_records(video_id);

-- video_likes
CREATE TABLE video_likes (
    id              BIGSERIAL PRIMARY KEY,
    video_id        BIGINT NOT NULL REFERENCES videos(id),
    customer_id     BIGINT NOT NULL REFERENCES customers(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(video_id, customer_id)
);

-- user_follows
CREATE TABLE user_follows (
    id              BIGSERIAL PRIMARY KEY,
    follower_id     BIGINT NOT NULL REFERENCES customers(id),
    followed_id     BIGINT NOT NULL REFERENCES customers(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(follower_id, followed_id),
    CHECK(follower_id != followed_id)
);
CREATE INDEX idx_follows_followed ON user_follows(followed_id);

-- task_definitions
CREATE TABLE task_definitions (
    id              BIGSERIAL PRIMARY KEY,
    task_code       VARCHAR(100) UNIQUE NOT NULL,
    task_name       VARCHAR(255) NOT NULL,
    description     TEXT,
    task_type       SMALLINT NOT NULL,
    task_category   SMALLINT NOT NULL,
    target_count    INT NOT NULL,
    reward_diamonds INT NOT NULL,
    icon_url        VARCHAR(500),
    display_order   INT NOT NULL DEFAULT 0,
    vip_only        BOOLEAN NOT NULL DEFAULT FALSE,
    status          SMALLINT NOT NULL DEFAULT 1,
    start_time      TIMESTAMPTZ,
    end_time        TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- task_progress
CREATE TABLE task_progress (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     BIGINT NOT NULL REFERENCES customers(id),
    task_id         BIGINT NOT NULL REFERENCES task_definitions(id),
    current_count   INT NOT NULL DEFAULT 0,
    target_count    INT NOT NULL,
    period_key      VARCHAR(50) NOT NULL,
    task_status     SMALLINT NOT NULL DEFAULT 1,
    reward_diamonds INT NOT NULL,
    completed_at    TIMESTAMPTZ,
    claimed_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(customer_id, task_id, period_key)
);
CREATE INDEX idx_task_progress_customer ON task_progress(customer_id);

-- +goose Down
DROP TABLE IF EXISTS task_progress;
DROP TABLE IF EXISTS task_definitions;
DROP TABLE IF EXISTS user_follows;
DROP TABLE IF EXISTS video_likes;
DROP TABLE IF EXISTS video_play_records;
DROP TABLE IF EXISTS videos;
DROP TABLE IF EXISTS customers;
DROP TABLE IF EXISTS admin_users;
