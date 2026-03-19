create table activity_reward_config
(
    id              bigint auto_increment comment '主键ID'
        primary key,
    config_type     varchar(50)                              not null comment '配置类型: SIGN_IN_MILESTONE / DAILY_SIGN_IN / INVITE_REWARD',
    config_key      varchar(50)                              not null comment '配置键（里程碑=天数, 日签=daily, 邀请=invite）',
    reward_diamonds int         default 0                    not null comment '奖励钻石数',
    display_order   int         default 0                    null comment '显示顺序',
    status          tinyint     default 1                    not null comment '状态: 0=禁用, 1=启用',
    create_time     datetime(3) default CURRENT_TIMESTAMP(3) not null comment '创建时间',
    create_by       varchar(64)                              null comment '创建人',
    update_time     datetime(3) default CURRENT_TIMESTAMP(3) not null on update CURRENT_TIMESTAMP(3) comment '更新时间',
    update_by       varchar(64)                              null comment '修改人',
    deleted_flag    datetime(3)                              null comment '逻辑删除标识',
    constraint uk_type_key
        unique (config_type, config_key, deleted_flag)
)
    comment '活动奖励配置表';

create index idx_config_type
    on activity_reward_config (config_type);

create index idx_status
    on activity_reward_config (status);

create table admin_users
(
    id            bigint auto_increment comment '主键ID'
        primary key,
    username      varchar(100)                        not null comment '账户名',
    password_hash varchar(255)                        not null comment '密码哈希值(例如bcrypt加密) | Hashed password (e.g., bcrypt)',
    role          tinyint                             not null comment '角色: 1=admin(管理员), 2=normal(普通用户)',
    full_name     varchar(255)                        null comment '全名',
    phone         varchar(50)                         null comment '电话',
    avatar_url    varchar(500)                        null comment '头像URL',
    status        tinyint   default 1                 not null comment '账户状态: 0=disabled(禁用), 1=enabled(启用)',
    last_login_at timestamp                           null comment '最后登录时间',
    create_time   timestamp default CURRENT_TIMESTAMP null comment '创建时间',
    create_by     varchar(100)                        null comment '创建人',
    update_time   timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    update_by     varchar(100)                        null comment '修改人',
    deleted_flag  timestamp                           null comment '逻辑删除标识（时间戳）NULL表示可用，有值表示已删除',
    constraint username
        unique (username)
)
    comment '管理员用户表' collate = utf8mb4_unicode_ci;

create index idx_deleted_flag
    on admin_users (deleted_flag);

create index idx_role
    on admin_users (role);

create index idx_status
    on admin_users (status);

create index idx_status_deleted
    on admin_users (status, deleted_flag);

create index idx_username
    on admin_users (username);

create table customer_sign_in_records
(
    id               bigint auto_increment comment '主键ID'
        primary key,
    customer_id      bigint                                   not null comment '客户ID',
    sign_in_date     date                                     not null comment '签到日期',
    period_key       varchar(10)                              not null comment '月份键 (e.g. 2026-03)',
    diamonds_awarded int         default 0                    not null comment '本次签到获得的钻石（日签+里程碑）',
    create_time      datetime(3) default CURRENT_TIMESTAMP(3) not null comment '创建时间',
    create_by        varchar(64)                              null comment '创建人',
    update_time      datetime(3) default CURRENT_TIMESTAMP(3) not null on update CURRENT_TIMESTAMP(3) comment '更新时间',
    update_by        varchar(64)                              null comment '修改人',
    deleted_flag     datetime(3)                              null comment '逻辑删除标识',
    constraint uk_customer_date
        unique (customer_id, sign_in_date)
)
    comment '客户签到记录表';

create index idx_customer_period
    on customer_sign_in_records (customer_id, period_key);

create index idx_sign_in_date
    on customer_sign_in_records (sign_in_date);

create table customer_task_progress
(
    id                bigint auto_increment comment '主键ID'
        primary key,
    customer_id       bigint                                   not null comment '客户ID | Customer ID (references customers.id)',
    task_id           bigint                                   not null comment '任务ID | Task ID (references task_definitions.id)',
    task_code         varchar(100)                             not null comment '任务编码 | Task code (denormalized for quick lookup)',
    current_count     int         default 0                    not null comment '当前进度 | Current progress count',
    target_count      int                                      not null comment '目标数量 | Target count (denormalized from task_definitions)',
    period_key        varchar(50)                              not null comment '周期标识 | Period key (e.g., 2026-02-05 for daily, 2026-W06 for weekly, 2026-02 for monthly)',
    period_start_time datetime(3)                              not null comment '周期开始时间 | Period start time',
    period_end_time   datetime(3)                              not null comment '周期结束时间 | Period end time',
    task_status       tinyint     default 1                    not null comment '任务状态: 1=in_progress(进行中), 2=completed(已完成), 3=claimed(已领取), 4=expired(已过期)',
    reward_status     tinyint     default 0                    not null comment '奖励状态: 0=not_claimable(不可领取), 1=claimable(可领取), 2=claimed(已领取)',
    completed_time    datetime(3)                              null comment '完成时间 | Task completion time',
    claimed_time      datetime(3)                              null comment '领取时间 | Reward claim time',
    reward_diamonds   int         default 0                    null comment '奖励钻石 | Reward diamonds',
    create_time       datetime(3) default CURRENT_TIMESTAMP(3) not null comment '创建时间',
    create_by         varchar(64)                              null comment '创建人',
    update_time       datetime(3) default CURRENT_TIMESTAMP(3) not null on update CURRENT_TIMESTAMP(3) comment '更新时间',
    update_by         varchar(64)                              null comment '修改人',
    deleted_flag      datetime(3)                              null comment '逻辑删除标识（时间戳）NULL表示可用，有值表示已删除',
    constraint uk_customer_task_period
        unique (customer_id, task_id, period_key, deleted_flag)
)
    comment '客户任务进度表 | Customer task progress table';

create index idx_completed_time
    on customer_task_progress (completed_time)
    comment 'Query by completion time';

create index idx_customer_id
    on customer_task_progress (customer_id)
    comment 'Query by customer';

create index idx_customer_period
    on customer_task_progress (customer_id, period_key)
    comment 'Query customer tasks in period';

create index idx_customer_task_period
    on customer_task_progress (customer_id, task_code, period_key)
    comment 'Unique task progress per period';

create index idx_period_key
    on customer_task_progress (period_key)
    comment 'Query by period';

create index idx_reward_status
    on customer_task_progress (reward_status)
    comment 'Query claimable rewards';

create index idx_task_id
    on customer_task_progress (task_id)
    comment 'Query by task';

create index idx_task_status
    on customer_task_progress (task_status)
    comment 'Query by status';

create table customer_transactions
(
    id               bigint auto_increment
        primary key,
    customer_id      bigint                              not null comment '客户ID',
    transaction_type tinyint                             not null comment '交易类型: 0=提现, 1=充值, 2=观看奖励, 3=任务奖励, 4=签到奖励, 5=邀请奖励',
    amount           bigint                              not null comment '金额(钻石数量)',
    status           tinyint   default 1                 not null comment '状态: 1=待处理, 2=成功, 3=失败',
    payment_method   varchar(50)                         null comment '支付方式',
    transaction_no   varchar(100)                        null comment '交易流水号',
    remark           varchar(500)                        null comment '备注',
    transaction_time timestamp                           not null comment '交易时间',
    create_time      timestamp default CURRENT_TIMESTAMP null,
    update_time      timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    create_by        varchar(100)                        null,
    update_by        varchar(100)                        null,
    deleted_flag     timestamp                           null
)
    comment '客户交易记录表';

create index idx_customer_id
    on customer_transactions (customer_id);

create index idx_status
    on customer_transactions (status);

create index idx_transaction_time
    on customer_transactions (transaction_time);

create index idx_transaction_type
    on customer_transactions (transaction_type);

create table customer_cash_orders
(
    id                   bigint auto_increment
        primary key,
    order_no             varchar(64)                          not null comment '订单号',
    customer_id          bigint                               not null comment '客户ID',
    customer_username    varchar(100)                         null comment '客户用户名快照',
    customer_name        varchar(255)                         null comment '客户昵称快照',
    order_type           tinyint                              not null comment '订单类型: 1=提现, 2=充值',
    status               tinyint                              not null comment '状态: 1=待审核, 2=已拒绝, 3=处理中, 4=成功, 5=失败',
    fiat_amount          decimal(18, 2)                       not null comment '法币金额',
    diamond_amount       bigint                               not null comment '钻石数量',
    currency_code        varchar(16) default 'CNY'            not null comment '法币币种',
    exchange_rate        decimal(18, 4)                       not null comment '汇率（每1 CNY对应钻石数）',
    payment_channel      varchar(50)                          null comment '支付渠道',
    provider_name        varchar(50)                          null comment '第三方平台名称',
    provider_order_no    varchar(100)                         null comment '第三方订单号',
    provider_status      varchar(50)                          null comment '第三方状态',
    reviewed_by_admin_id bigint                               null comment '审核管理员ID',
    reviewed_time        timestamp                            null comment '审核时间',
    failure_reason       varchar(255)                         null comment '失败或拒绝原因',
    completed_time       timestamp                            null comment '完成时间',
    external_reference   varchar(100)                         null comment '外部引用号',
    create_time          timestamp default CURRENT_TIMESTAMP  null,
    update_time          timestamp default CURRENT_TIMESTAMP  null on update CURRENT_TIMESTAMP,
    create_by            varchar(100)                         null,
    update_by            varchar(100)                         null,
    deleted_flag         timestamp                            null,
    constraint uk_cash_order_no
        unique (order_no)
)
    comment '客户资金订单表';

create index idx_cash_order_customer
    on customer_cash_orders (customer_id);

create index idx_cash_order_type_status
    on customer_cash_orders (order_type, status);

create index idx_cash_order_create_time
    on customer_cash_orders (create_time);

create table customers
(
    id                       bigint auto_increment comment '主键ID'
        primary key,
    username                 varchar(100)                        not null comment '账户名',
    password_hash            varchar(255)                        not null comment '密码哈希值 | Hashed password',
    name                     varchar(255)                        not null comment '客户名称',
    phone                    varchar(50)                         null comment '电话',
    is_vip                   tinyint   default 0                 not null comment 'VIP状态: 0=non-VIP(普通用户), 1=VIP(会员)',
    avatar_url               varchar(500)                        null comment '客户头像URL | Customer avatar URL',
    email                    varchar(255)                        null comment '邮箱地址',
    profile_signature        varchar(255)                        null comment '个性签名',
    withdrawal_password_hash varchar(255)                        null comment '取款密码',
    registration_time        timestamp default CURRENT_TIMESTAMP null comment '注册时间',
    last_login_at            timestamp                           null comment '最后登录时间',
    create_time              timestamp default CURRENT_TIMESTAMP null comment '创建时间',
    create_by                varchar(100)                        null comment '创建人',
    update_time              timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    update_by                varchar(100)                        null comment '修改人',
    deleted_flag             timestamp                           null comment '逻辑删除标识（时间戳）NULL表示可用，有值表示已删除',
    diamond_balance          bigint    default 0                 not null comment '钻石余额 | Diamond balance',
    frozen_diamond_balance   bigint    default 0                 not null comment '冻结钻石余额 | Frozen diamond balance',
    invite_code              varchar(8)                          null comment '邀请码（8位字母+数字）',
    invited_by               bigint                              null comment '邀请人customer_id',
    constraint invite_code
        unique (invite_code),
    constraint username
        unique (username)
)
    comment '客户表' collate = utf8mb4_unicode_ci;

create index idx_deleted_flag
    on customers (deleted_flag);

create index idx_email
    on customers (email);

create index idx_is_vip
    on customers (is_vip);

create index idx_name
    on customers (name);

create index idx_registration_time
    on customers (registration_time);

create index idx_username
    on customers (username);

create table daily_video_reports
(
    id                bigint auto_increment
        primary key,
    report_date       varchar(20)                         not null comment '报表日期',
    customer_id       bigint                              not null comment '客户ID',
    customer_name     varchar(100)                        null comment '客户姓名',
    is_vip            tinyint                             null comment 'VIP状态: 0=普通, 1=VIP',
    click_count       int       default 0                 null comment '点击次数',
    valid_count       int       default 0                 null comment '有效次数',
    settlement_amount bigint    default 0                 null comment '应结算金额(钻石)',
    create_time       timestamp default CURRENT_TIMESTAMP null,
    update_time       timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    create_by         varchar(100)                        null,
    update_by         varchar(100)                        null,
    deleted_flag      timestamp                           null,
    constraint uk_date_customer
        unique (report_date, customer_id)
)
    comment '每日视频报表';

create index idx_customer_id
    on daily_video_reports (customer_id);

create index idx_report_date
    on daily_video_reports (report_date);

create table task_definitions
(
    id               bigint auto_increment comment '主键ID'
        primary key,
    task_code        varchar(100)                             not null comment '任务唯一编码 | Unique task code (e.g., DAILY_WATCH_VIDEO_5)',
    task_name        varchar(255)                             not null comment '任务名称 | Task name',
    task_description text                                     null comment '任务描述 | Task description',
    task_type        tinyint                                  not null comment '任务类型: 1=daily(每日), 2=weekly(每周), 3=monthly(每月)',
    task_category    tinyint                                  not null comment '任务分类: 1=watch_video(短剧), 2=upload_video, 3=share_video, 4=comment, 5=like, 6=login, 7=invite_friend, 8=watch_ad(广告)',
    target_count     int         default 1                    not null comment '目标数量 | Target count to complete',
    reward_diamonds  int         default 0                    not null comment '奖励钻石 | Reward diamonds',
    icon_url         varchar(500)                             null comment '任务图标URL | Task icon URL',
    display_order    int         default 0                    null comment '显示顺序 | Display order (lower number = higher priority)',
    start_time       datetime(3)                              null comment '任务开始时间 | Task start time (NULL = always available)',
    end_time         datetime(3)                              null comment '任务结束时间 | Task end time (NULL = no expiration)',
    status           tinyint     default 1                    not null comment '任务状态: 0=disabled(禁用), 1=enabled(启用)',
    vip_only         tinyint     default 0                    not null comment 'VIP专属: 0=all users, 1=VIP only',
    min_level        int         default 0                    null comment '最低等级要求 | Minimum user level required',
    create_time      datetime(3) default CURRENT_TIMESTAMP(3) not null comment '创建时间',
    create_by        varchar(64)                              null comment '创建人',
    update_time      datetime(3) default CURRENT_TIMESTAMP(3) not null on update CURRENT_TIMESTAMP(3) comment '更新时间',
    update_by        varchar(64)                              null comment '修改人',
    deleted_flag     datetime(3)                              null comment '逻辑删除标识（时间戳）NULL表示可用，有值表示已删除',
    constraint task_code
        unique (task_code)
)
    comment '任务定义表 | Task definitions table';

create index idx_display_order
    on task_definitions (display_order)
    comment 'Sort by display order';

create index idx_status
    on task_definitions (status)
    comment 'Query active tasks';

create index idx_task_category
    on task_definitions (task_category)
    comment 'Query by category';

create index idx_task_type
    on task_definitions (task_type)
    comment 'Query by task type';

create index idx_task_type_status
    on task_definitions (task_type, status, deleted_flag)
    comment 'Query active tasks by type';

create index idx_vip_only
    on task_definitions (vip_only)
    comment 'Filter VIP tasks';

create table user_follow
(
    id          bigint auto_increment comment '主键ID'
        primary key,
    follower_id bigint                              not null comment '关注者用户ID | 关注者用户ID（关联 customers.id）',
    followed_id bigint                              not null comment '被关注用户ID | 被关注用户ID（关联 customers.id）',
    status      tinyint   default 1                 not null comment '关注状态: 0=unfollowed(取消关注), 1=followed(关注)',
    follow_time timestamp default CURRENT_TIMESTAMP not null comment '关注时间',
    create_time timestamp default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_follower_followed
        unique (follower_id, followed_id) comment '防止重复关注（同一个人不能重复关注同一个人）'
)
    comment '用户关注表 | 用户关注表';

create index idx_followed_id
    on user_follow (followed_id)
    comment '按被关注者查询（关注我的人）';

create index idx_followed_status
    on user_follow (followed_id, status)
    comment '按被关注者查询活跃粉丝';

create index idx_follower_id
    on user_follow (follower_id)
    comment '按关注者查询（我关注的人）';

create index idx_follower_status
    on user_follow (follower_id, status)
    comment '按关注者查询活跃关注';

create index idx_status
    on user_follow (status)
    comment '按关注状态查询';

create table video_like_record
(
    id        bigint auto_increment comment '主键ID'
        primary key,
    video_id  bigint                             not null comment '视频ID',
    user_id   bigint                             not null comment '用户ID',
    like_time datetime default CURRENT_TIMESTAMP not null comment '点赞时间',
    constraint uk_video_user
        unique (video_id, user_id) comment '防止重复点赞（同一用户不能重复点赞同一视频）'
)
    comment '视频点赞记录表' collate = utf8mb4_unicode_ci;

create index idx_user_id
    on video_like_record (user_id)
    comment '按用户ID查询';

create index idx_video_id
    on video_like_record (video_id)
    comment '按视频ID查询';

create index idx_video_time
    on video_like_record (video_id, like_time)
    comment '按视频ID和时间查询';

create table video_play_records
(
    id           bigint auto_increment comment 'Primary key'
        primary key,
    video_id     bigint                                   not null comment 'Video ID (foreign key to videos table)',
    customer_id  bigint                                   null comment '客户ID（可为NULL，表示未登录用户）',
    play_type    tinyint                                  not null comment 'Play type: 1=Click, 2=Valid Play (15s+)',
    ip_address   varchar(45)                              null comment 'User IP address extracted from request',
    play_time    datetime(3)                              not null comment 'Timestamp when the play event occurred',
    create_time  datetime(3) default CURRENT_TIMESTAMP(3) not null comment 'Record creation time',
    create_by    varchar(64)                              null comment 'Creator',
    update_time  datetime(3) default CURRENT_TIMESTAMP(3) not null on update CURRENT_TIMESTAMP(3) comment 'Last update time',
    update_by    varchar(64)                              null comment 'Last updater',
    deleted_flag datetime(3)                              null comment 'Soft delete timestamp (NULL = active)'
)
    comment 'Video playback records table';

create index idx_customer_id
    on video_play_records (customer_id)
    comment 'Query by customer';

create index idx_play_time
    on video_play_records (play_time)
    comment 'Query by time range';

create index idx_video_id
    on video_play_records (video_id)
    comment 'Query by video';

create index idx_video_play_type
    on video_play_records (video_id, play_type)
    comment 'Statistics by video and type';

create table videos
(
    id               bigint auto_increment comment '主键ID'
        primary key,
    customer_id      bigint                              not null comment '上传客户ID | Customer who uploaded (references customers.id)',
    uploader_name    varchar(255)                        not null comment '上传人名称 | Uploader name (denormalized)',
    title            varchar(500)                        null comment '视频标题 | Video title',
    tags             varchar(255)                        null comment '视频标签(逗号分隔)',
    is_uploader_vip  tinyint                             not null comment '上传时VIP状态 | VIP status at upload time (denormalized)',
    file_name        varchar(255)                        not null comment '文件名',
    file_size        bigint                              null comment '文件大小(字节) | File size in bytes',
    file_url         varchar(500)                        not null comment '视频文件URL | Video file URL',
    preview_gif_url  varchar(500)                        null comment '预览GIF URL | Preview GIF URL',
    thumbnail_url    varchar(500)                        null comment '缩略图URL | Thumbnail image URL',
    mime_type        varchar(100)                        null comment 'MIME类型, 例如: video/mp4',
    duration         int                                 null comment '视频时长(秒) | Video duration in seconds',
    width            int                                 null comment '视频宽度(像素) | Video width in pixels',
    height           int                                 null comment '视频高度(像素) | Video height in pixels',
    storage_provider tinyint   default 1                 null comment '存储提供商: 1=local(本地), 2=s3(亚马逊S3), 3=cdn(CDN)',
    storage_path     text                                null comment '完整存储路径或存储桶信息 | Full storage path or bucket info',
    reviewed_by      bigint                              null comment '审核人ID | Reviewer admin user ID (references admin_users.id)',
    reviewed_at      timestamp                           null comment '审核时间 | Review timestamp',
    review_notes     text                                null comment '审核备注 | Review notes',
    total_clicks     bigint    default 0                 null comment '总点击数 | Total clicks',
    valid_clicks     bigint    default 0                 null comment '有效点击数 | Valid clicks',
    upload_time      timestamp default CURRENT_TIMESTAMP null comment '上传时间 | Upload timestamp',
    like_count       bigint    default 0                 not null comment '点赞数',
    status           tinyint   default 0                 not null comment '视频状态: 0=失败, 1=等待转码, 2=转码中, 3=等待审核, 4=审核通过, 5=审核不通过',
    create_time      timestamp default CURRENT_TIMESTAMP null comment '创建时间',
    create_by        varchar(100)                        null comment '创建人',
    update_time      timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    update_by        varchar(100)                        null comment '修改人',
    deleted_flag     timestamp                           null comment '逻辑删除标识（时间戳）NULL表示可用，有值表示已删除',
    transcode_job_id varchar(100)                        null comment 'MediaConvert 转码任务ID'
)
    comment '视频表' collate = utf8mb4_unicode_ci;

create index idx_customer_id
    on videos (customer_id);

create index idx_deleted_flag
    on videos (deleted_flag);

create index idx_online_deleted
    on videos (deleted_flag);

create index idx_reviewed_by
    on videos (reviewed_by);

create index idx_status_deleted
    on videos (deleted_flag);

create index idx_upload_time
    on videos (upload_time);
