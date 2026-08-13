-- 商城 v3 数据收口：修正存量数据、补齐历史事件，并删除已下线的旧商城兼容表。
-- 本脚本只新增，不修改已登记的历史迁移；执行成功后商城只支持 v3 数据模型。
USE game_community;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 兜底创建 Outbox，允许已执行旧迁移但尚未创建该表的实例直接收口。
CREATE TABLE IF NOT EXISTS t_shop_order_paid_outbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL,
    order_no VARCHAR(40) NOT NULL,
    topic VARCHAR(128) NOT NULL,
    message_key VARCHAR(128) NOT NULL,
    payload JSON NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待发送 1发送中 2已发送 3重试 4死信',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lock_token VARCHAR(64) NOT NULL DEFAULT '',
    lock_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00',
    last_error VARCHAR(1000) NOT NULL DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_shop_paid_outbox_event (event_id),
    UNIQUE KEY uk_shop_paid_outbox_order (order_no),
    KEY idx_shop_paid_outbox_pending (status, next_retry_time, id),
    KEY idx_shop_paid_outbox_lock (status, lock_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商城支付 Kafka 事务 Outbox';

-- 存量商品统一为 v3 可购买数据，避免旧 NULL/非法值绕过库存和限购判断。
UPDATE t_shop_item
SET name = CASE WHEN name IS NULL OR TRIM(name) = '' THEN CONCAT('迁移商品-', id) ELSE TRIM(name) END,
    description = COALESCE(description, ''),
    cosmetic_code = CASE WHEN cosmetic_code IS NULL OR TRIM(cosmetic_code) = ''
                         THEN CONCAT('MIGRATED-', id) ELSE TRIM(cosmetic_code) END,
    price_points = GREATEST(COALESCE(price_points, 0), 0),
    grant_quantity = CASE WHEN grant_quantity IS NULL OR grant_quantity < 1 THEN 1 ELSE grant_quantity END,
    stock = CASE WHEN COALESCE(stock, -1) < -1 THEN -1 ELSE COALESCE(stock, -1) END,
    icon = COALESCE(icon, ''),
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

-- 订单快照只允许非负、可重试的 v3 数据。
UPDATE t_shop_order
SET item_name = CASE WHEN item_name IS NULL OR TRIM(item_name) = '' THEN CONCAT('迁移订单商品-', item_id) ELSE TRIM(item_name) END,
    item_icon = COALESCE(item_icon, ''),
    cosmetic_code = CASE WHEN cosmetic_code IS NULL OR TRIM(cosmetic_code) = ''
                         THEN CONCAT('MIGRATED-ORDER-', id) ELSE TRIM(cosmetic_code) END,
    quantity = CASE WHEN quantity IS NULL OR quantity < 1 THEN 1 ELSE quantity END,
    price_points = GREATEST(COALESCE(price_points, 0), 0),
    total_points = GREATEST(COALESCE(total_points, 0), 0),
    grant_quantity = CASE WHEN grant_quantity IS NULL OR grant_quantity < 1 THEN 1 ELSE grant_quantity END,
    fail_reason = COALESCE(fail_reason, ''),
    pay_time = COALESCE(pay_time, '1970-01-01 00:00:00'),
    complete_time = COALESCE(complete_time, '1970-01-01 00:00:00');

-- 积分账户统一保留为单一 points 余额，并补齐已注册用户缺失的账户行。
UPDATE t_shop_user_currency
SET points = GREATEST(COALESCE(points, 0), 0),
    update_time = NOW();

INSERT INTO t_shop_user_currency (user_id, points)
SELECT u.id, 0
FROM t_user u
LEFT JOIN t_shop_user_currency c ON c.user_id = u.id
WHERE c.id IS NULL;

-- 以订单状态重建限购计数：已支付/完成是 purchased，创建中/待支付是 reserved。
CREATE TEMPORARY TABLE tmp_shop_limit_rebuild AS
SELECT o.user_id,
       o.item_id,
       COALESCE(SUM(CASE WHEN o.status IN (3, 4) THEN o.quantity ELSE 0 END), 0) AS purchased_count,
       COALESCE(SUM(CASE WHEN o.status IN (1, 2) THEN o.quantity ELSE 0 END), 0) AS reserved_count,
       COALESCE(MAX(CASE WHEN o.status IN (3, 4) THEN COALESCE(o.complete_time, o.pay_time, o.create_time) END),
                '1970-01-01 00:00:00') AS last_purchase_at
FROM t_shop_order o
JOIN t_shop_item i ON i.id = o.item_id
WHERE i.repurchase_policy <> 'UNLIMITED'
GROUP BY o.user_id, o.item_id;

DELETE p
FROM t_shop_purchase_limit p
JOIN t_shop_item i ON i.id = p.item_id
WHERE i.repurchase_policy = 'UNLIMITED';

UPDATE t_shop_purchase_limit p
LEFT JOIN tmp_shop_limit_rebuild r ON r.user_id = p.user_id AND r.item_id = p.item_id
SET p.purchased_count = COALESCE(r.purchased_count, 0),
    p.reserved_count = COALESCE(r.reserved_count, 0),
    p.last_purchase_at = COALESCE(r.last_purchase_at, '1970-01-01 00:00:00'),
    p.update_time = NOW();

INSERT INTO t_shop_purchase_limit
    (user_id, item_id, purchased_count, reserved_count, last_purchase_at, window_start_at, create_time, update_time)
SELECT user_id, item_id, purchased_count, reserved_count, last_purchase_at,
       '1970-01-01 00:00:00', NOW(), NOW()
FROM tmp_shop_limit_rebuild
ON DUPLICATE KEY UPDATE
    purchased_count = VALUES(purchased_count),
    reserved_count = VALUES(reserved_count),
    last_purchase_at = VALUES(last_purchase_at),
    update_time = NOW();

DROP TEMPORARY TABLE tmp_shop_limit_rebuild;

-- 历史已支付订单补发任务和 Outbox；已完成订单标记为已处理，避免重复发放。
INSERT INTO t_shop_delivery_task(order_no, status, retry_count, next_retry_time)
SELECT o.order_no, CASE WHEN o.status = 4 THEN 2 ELSE 0 END, 0, NOW()
FROM t_shop_order o
WHERE o.status IN (3, 4)
ON DUPLICATE KEY UPDATE order_no = VALUES(order_no);

INSERT INTO t_shop_order_paid_outbox
    (event_id, order_no, topic, message_key, payload, status, retry_count, next_retry_time,
     lock_token, lock_time, last_error, create_time, update_time)
SELECT CONCAT('MIGRATED-PAID-', o.id),
       o.order_no,
       'shop-order-paid-events',
       o.order_no,
       JSON_OBJECT(
           'eventId', CONCAT('MIGRATED-PAID-', o.id),
           'schemaVersion', 1,
           'orderNo', o.order_no,
           'userId', o.user_id,
           'itemId', o.item_id,
           'cosmeticCode', o.cosmetic_code,
           'quantity', o.quantity,
           'grantQuantity', o.grant_quantity,
           'paidAt', DATE_FORMAT(COALESCE(NULLIF(o.pay_time, '1970-01-01 00:00:00'), o.create_time), '%Y-%m-%dT%H:%i:%s')
       ),
       CASE WHEN o.status = 4 THEN 2 ELSE 0 END,
       0,
       NOW(),
       '',
       '1970-01-01 00:00:00',
       '',
       NOW(),
       NOW()
FROM t_shop_order o
WHERE o.status IN (3, 4)
ON DUPLICATE KEY UPDATE order_no = VALUES(order_no);

-- 优惠券已从 v3 订单模型中移除，不再保留表或任何旧数据兼容入口。
DROP TABLE IF EXISTS t_shop_user_coupon;
DROP TABLE IF EXISTS t_shop_coupon;

SET FOREIGN_KEY_CHECKS = 1;
