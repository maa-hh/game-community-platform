-- 商城 v3 生产迁移：从 Redis List + 旧限购模型迁移到原子预占、持久化订单任务模型。
-- 本脚本只执行一次，禁止在已执行后修改；已有优惠券表属于已下线能力，直接删除。
USE game_community;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 同一装扮只保留最早商品记录，历史订单和限购记录统一指向主商品。
CREATE TEMPORARY TABLE tmp_shop_item_canonical AS
SELECT cosmetic_code, MIN(id) AS canonical_id
FROM t_shop_item
GROUP BY cosmetic_code;

UPDATE t_shop_order o
JOIN t_shop_item i ON i.id = o.item_id
JOIN tmp_shop_item_canonical c ON c.cosmetic_code = i.cosmetic_code
SET o.item_id = c.canonical_id
WHERE i.id <> c.canonical_id;

UPDATE t_shop_purchase_limit p
JOIN t_shop_item i ON i.id = p.item_id
JOIN tmp_shop_item_canonical c ON c.cosmetic_code = i.cosmetic_code
SET p.item_id = c.canonical_id
WHERE i.id <> c.canonical_id;

DELETE i
FROM t_shop_item i
JOIN tmp_shop_item_canonical c ON c.cosmetic_code = i.cosmetic_code
WHERE i.id <> c.canonical_id;

DROP TEMPORARY TABLE tmp_shop_item_canonical;

UPDATE t_shop_item
SET status = 0
WHERE cosmetic_code IS NULL OR TRIM(cosmetic_code) = '';

UPDATE t_shop_item
SET name = CASE WHEN name IS NULL OR TRIM(name) = '' THEN CONCAT('迁移商品-', id) ELSE TRIM(name) END,
    cosmetic_code = CASE WHEN cosmetic_code IS NULL OR TRIM(cosmetic_code) = ''
                          THEN CONCAT('MIGRATED-', id) ELSE TRIM(cosmetic_code) END;

UPDATE t_shop_item
SET description = COALESCE(description, ''),
    icon = COALESCE(icon, ''),
    price_points = GREATEST(COALESCE(price_points, 0), 0),
    stock = CASE WHEN COALESCE(stock, -1) < -1 THEN -1 ELSE COALESCE(stock, -1) END,
    grant_quantity = CASE WHEN grant_quantity IS NULL OR grant_quantity < 1 THEN 1 ELSE grant_quantity END,
    status = CASE WHEN status = 1 THEN 1 ELSE 0 END,
    repurchase_policy = CASE WHEN repurchase_policy IN ('ONCE_FOREVER', 'UNLIMITED', 'COOLDOWN', 'LIMIT_PER_WINDOW')
                             THEN repurchase_policy ELSE 'ONCE_FOREVER' END,
    limit_count = CASE WHEN limit_count IS NULL OR limit_count <= 0 THEN -1 ELSE limit_count END,
    limit_window_seconds = CASE WHEN limit_window_seconds IS NULL OR limit_window_seconds < 0 THEN 0 ELSE limit_window_seconds END,
    begin_time = COALESCE(begin_time, '1970-01-01 00:00:00'),
    end_time = COALESCE(end_time, '9999-12-31 23:59:59');

UPDATE t_shop_item
SET limit_window_seconds = 86400
WHERE repurchase_policy IN ('COOLDOWN', 'LIMIT_PER_WINDOW')
  AND limit_window_seconds <= 0;

UPDATE t_shop_item
SET limit_window_seconds = 0
WHERE repurchase_policy IN ('ONCE_FOREVER', 'UNLIMITED');

ALTER TABLE t_shop_item
    MODIFY description VARCHAR(500) NOT NULL DEFAULT '',
    MODIFY cosmetic_code VARCHAR(64) NOT NULL,
    MODIFY price_points BIGINT NOT NULL,
    MODIFY grant_quantity INT NOT NULL DEFAULT 1,
    MODIFY stock INT NOT NULL DEFAULT -1,
    MODIFY icon VARCHAR(500) NOT NULL DEFAULT '',
    MODIFY status TINYINT NOT NULL DEFAULT 1,
    MODIFY repurchase_policy VARCHAR(32) NOT NULL DEFAULT 'ONCE_FOREVER',
    MODIFY limit_count INT NOT NULL DEFAULT -1,
    MODIFY limit_window_seconds INT NOT NULL DEFAULT 0,
    MODIFY begin_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00',
    MODIFY end_time DATETIME NOT NULL DEFAULT '9999-12-31 23:59:59',
    DROP INDEX idx_shop_item_status,
    DROP INDEX idx_shop_item_cosmetic,
    ADD KEY idx_shop_item_status_time (status, create_time, id),
    ADD UNIQUE KEY uk_shop_item_cosmetic (cosmetic_code),
    ADD CONSTRAINT chk_shop_item_stock CHECK (stock = -1 OR stock >= 0),
    ADD CONSTRAINT chk_shop_item_price CHECK (price_points >= 0),
    ADD CONSTRAINT chk_shop_item_grant CHECK (grant_quantity > 0),
    ADD CONSTRAINT chk_shop_item_limit CHECK (limit_count = -1 OR limit_count > 0),
    ADD CONSTRAINT chk_shop_item_window CHECK (limit_window_seconds >= 0);

UPDATE t_shop_order
SET item_name = CASE WHEN item_name IS NULL OR TRIM(item_name) = '' THEN CONCAT('迁移订单商品-', item_id) ELSE item_name END,
    item_icon = COALESCE(item_icon, ''),
    cosmetic_code = CASE WHEN cosmetic_code IS NULL OR TRIM(cosmetic_code) = ''
                          THEN CONCAT('MIGRATED-ORDER-', id) ELSE cosmetic_code END,
    quantity = CASE WHEN quantity IS NULL OR quantity < 1 THEN 1 ELSE quantity END,
    price_points = GREATEST(COALESCE(price_points, 0), 0),
    total_points = GREATEST(COALESCE(total_points, 0), 0),
    grant_quantity = CASE WHEN grant_quantity IS NULL OR grant_quantity < 1 THEN 1 ELSE grant_quantity END,
    fail_reason = COALESCE(fail_reason, ''),
    pay_time = COALESCE(pay_time, '1970-01-01 00:00:00'),
    complete_time = COALESCE(complete_time, '1970-01-01 00:00:00');

ALTER TABLE t_shop_order
    MODIFY item_name VARCHAR(100) NOT NULL DEFAULT '',
    MODIFY item_icon VARCHAR(500) NOT NULL DEFAULT '',
    MODIFY price_points BIGINT NOT NULL,
    MODIFY total_points BIGINT NOT NULL,
    MODIFY fail_reason VARCHAR(255) NOT NULL DEFAULT '',
    MODIFY pay_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00',
    MODIFY complete_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00',
    ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER update_time,
    DROP INDEX idx_user_status_time,
    ADD KEY idx_shop_order_user_time (user_id, create_time, id),
    ADD KEY idx_shop_order_item_status (item_id, status, create_time),
    ADD CONSTRAINT chk_shop_order_quantity CHECK (quantity > 0),
    ADD CONSTRAINT chk_shop_order_total CHECK (total_points >= 0);

ALTER TABLE t_shop_purchase_limit
    ADD COLUMN reserved_count INT NOT NULL DEFAULT 0 AFTER purchased_count,
    MODIFY last_purchase_at DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00',
    MODIFY window_start_at DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00';

TRUNCATE TABLE t_shop_purchase_limit;
INSERT INTO t_shop_purchase_limit
    (user_id, item_id, purchased_count, reserved_count, last_purchase_at, window_start_at, create_time, update_time)
SELECT user_id,
       item_id,
       COALESCE(SUM(CASE WHEN status IN (3, 4) THEN quantity ELSE 0 END), 0),
       COALESCE(SUM(CASE WHEN status IN (1, 2) THEN quantity ELSE 0 END), 0),
       COALESCE(MAX(CASE WHEN status IN (3, 4) THEN COALESCE(complete_time, pay_time, create_time) END), '1970-01-01 00:00:00'),
       '1970-01-01 00:00:00',
       NOW(), NOW()
FROM t_shop_order
GROUP BY user_id, item_id;

UPDATE t_shop_points_ledger
SET biz_ref = CONCAT('MIGRATED-', id),
    remark = COALESCE(remark, '')
WHERE biz_ref IS NULL OR biz_ref = '';

ALTER TABLE t_shop_points_ledger
    MODIFY biz_ref VARCHAR(64) NOT NULL DEFAULT '',
    MODIFY remark VARCHAR(255) NOT NULL DEFAULT '',
    ADD UNIQUE KEY uk_shop_ledger_biz (biz_type, biz_ref),
    DROP INDEX idx_ledger_user_time,
    ADD KEY idx_shop_ledger_user_time (user_id, create_time, id);

DROP TABLE IF EXISTS t_shop_user_coupon;
DROP TABLE IF EXISTS t_shop_coupon;

CREATE TABLE t_shop_delivery_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(40) NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1处理中 2已完成 3重试 4死信',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lock_token VARCHAR(64) NOT NULL DEFAULT '',
    lock_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00',
    last_error VARCHAR(1000) NOT NULL DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_shop_delivery_order (order_no),
    KEY idx_shop_delivery_pending (status, next_retry_time, id),
    KEY idx_shop_delivery_lock (status, lock_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商城权益发放任务';

INSERT IGNORE INTO t_shop_delivery_task(order_no, status, retry_count, next_retry_time)
SELECT order_no, 0, 0, NOW()
FROM t_shop_order
WHERE status = 3;

SET FOREIGN_KEY_CHECKS = 1;
