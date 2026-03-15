-- Change transaction_type comment
ALTER TABLE customer_transactions
MODIFY COLUMN transaction_type tinyint NOT NULL COMMENT '交易类型: 0=提现(Withdraw), 1=充值(Recharge), 2=观看奖励(Watch Reward), 3=任务奖励(Task Reward), 4=签到奖励(Sign-in), 5=邀请奖励(Invite)';
