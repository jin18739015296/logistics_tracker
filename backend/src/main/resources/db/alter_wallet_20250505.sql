-- ============================================
-- 钱包表结构变更脚本（2025-05-05）
-- 适用于已存在 courier_wallets / wallet_transaction 的数据库
-- ============================================

-- --------------------------------------------
-- 1. 新建 wallets 表（替代 courier_wallets）
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS wallets (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '钱包ID',
    user_id BIGINT NOT NULL UNIQUE COMMENT '用户ID（用户或配送员）',

    balance DECIMAL(10,2) DEFAULT 0 COMMENT '余额',
    frozen_amount DECIMAL(10,2) DEFAULT 0 COMMENT '冻结金额',

    total_income DECIMAL(10,2) DEFAULT 0 COMMENT '累计收入',
    total_withdraw DECIMAL(10,2) DEFAULT 0 COMMENT '累计提现',

    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包表';

-- 迁移 courier_wallets 数据到 wallets（courier_id → user_id）
INSERT INTO wallets (user_id, balance, frozen_amount, total_income, total_withdraw, update_time)
SELECT courier_id, balance, frozen_amount, total_income, total_withdraw, update_time
FROM courier_wallets
ON DUPLICATE KEY UPDATE
    balance = VALUES(balance),
    frozen_amount = VALUES(frozen_amount),
    total_income = VALUES(total_income),
    total_withdraw = VALUES(total_withdraw),
    update_time = VALUES(update_time);

-- 删除旧表（确认数据迁移无误后执行）
-- DROP TABLE IF EXISTS courier_wallets;

-- --------------------------------------------
-- 2. 修改已存在的 wallet_transaction 表
-- --------------------------------------------

-- 2.1 重命名列 courier_id → user_id
ALTER TABLE wallet_transaction
    CHANGE COLUMN courier_id user_id BIGINT NOT NULL COMMENT '用户ID（用户或配送员）';

-- 2.2 重建索引（先删后建）
ALTER TABLE wallet_transaction
    DROP INDEX IF EXISTS idx_courier,
    ADD INDEX idx_user (user_id);

-- 2.3 添加 order_no 字段（如果尚不存在）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                   AND table_name = 'wallet_transaction'
                   AND column_name = 'order_no');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE wallet_transaction ADD COLUMN order_no VARCHAR(32) COMMENT "关联订单号" AFTER order_id',
    'SELECT "order_no 已存在，跳过" AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2.4 添加 order_id 索引（如果尚不存在）
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
                   WHERE table_schema = DATABASE()
                   AND table_name = 'wallet_transaction'
                   AND index_name = 'idx_order');
SET @sql2 = IF(@idx_exists = 0,
    'ALTER TABLE wallet_transaction ADD INDEX idx_order (order_id)',
    'SELECT "idx_order 已存在，跳过" AS msg');
PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- 2.5 扩展 type 枚举说明（仅注释，业务层控制）
-- 支持类型: income-收入 / withdraw-提现 / refund-退款 / payment-支付
