-- 商城一致性压测/回滚场景（仅测试环境执行）。
-- 需要先执行 cosmetic.sql、shop.sql 及 shop-v3-migration.sql。
USE game_community;
SET NAMES utf8mb4;

-- 取前 5 个用户作为压测账号，并补足余额；不要在生产环境执行本文件。
INSERT INTO t_shop_user_currency (user_id, points)
SELECT id, 100000
FROM t_user
ORDER BY id
LIMIT 5
ON DUPLICATE KEY UPDATE points = 100000, update_time = CURRENT_TIMESTAMP;

-- 场景 1：每人窗口限购 3 个，库存 12。并发同一用户请求应最多成功 3 个。
INSERT INTO t_shop_item (id, name, description, cosmetic_code, price_points, grant_quantity, stock, icon, status,
                         repurchase_policy, limit_count, limit_window_seconds, begin_time, end_time)
VALUES (101, '测试-窗口限购3件', '用于验证同一用户在窗口内的累计限购与并发幂等', 'profile_bg_hornet_soft', 100, 1, 12,
        '', 1, 'LIMIT_PER_WINDOW', 3, 60, NOW() - INTERVAL 1 MINUTE, NOW() + INTERVAL 1 DAY)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), price_points = VALUES(price_points), stock = VALUES(stock), status = 1,
    repurchase_policy = VALUES(repurchase_policy), limit_count = VALUES(limit_count),
    limit_window_seconds = VALUES(limit_window_seconds), begin_time = VALUES(begin_time), end_time = VALUES(end_time);

-- 场景 2：每人永久限购 1 个，库存 5。可验证 Redis bitmap + MySQL 行锁双重防重。
INSERT INTO t_shop_item (id, name, description, cosmetic_code, price_points, grant_quantity, stock, icon, status,
                         repurchase_policy, limit_count, limit_window_seconds, begin_time, end_time)
VALUES (102, '测试-永久限购1件', '用于验证同一用户重复点击和多节点并发只成功一次', 'avatar_frame_royal_crown', 200, 1, 5,
        '', 1, 'ONCE_FOREVER', 1, 0, NOW() - INTERVAL 1 MINUTE, NOW() + INTERVAL 1 DAY)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), price_points = VALUES(price_points), stock = VALUES(stock), status = 1,
    repurchase_policy = VALUES(repurchase_policy), limit_count = VALUES(limit_count),
    limit_window_seconds = VALUES(limit_window_seconds), begin_time = VALUES(begin_time), end_time = VALUES(end_time);

-- 场景 3：模拟秒杀，20 件有限库存、10 分钟窗口、单价 1 积分。多个用户并发购买不应超卖。
INSERT INTO t_shop_item (id, name, description, cosmetic_code, price_points, grant_quantity, stock, icon, status,
                         repurchase_policy, limit_count, limit_window_seconds, begin_time, end_time)
VALUES (103, '测试-10分钟秒杀20件', '用于验证有限库存、Redis Lua 原子预占和数据库库存兜底', 'avatar_frame_naval_blue', 1, 1, 20,
        '', 1, 'UNLIMITED', -1, 0, NOW() - INTERVAL 1 MINUTE, NOW() + INTERVAL 10 MINUTE)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), price_points = VALUES(price_points), stock = VALUES(stock), status = 1,
    repurchase_policy = VALUES(repurchase_policy), begin_time = VALUES(begin_time), end_time = VALUES(end_time);

-- 场景 4：每人每天限购 1 个，库存 8。用于验证窗口重置和失败订单释放。
INSERT INTO t_shop_item (id, name, description, cosmetic_code, price_points, grant_quantity, stock, icon, status,
                         repurchase_policy, limit_count, limit_window_seconds, begin_time, end_time)
VALUES (104, '测试-每日限购1件', '用于验证支付失败/取消后预占释放以及窗口限购', 'avatar_frame_shadow_lotus', 10, 1, 8,
        '', 1, 'LIMIT_PER_WINDOW', 1, 86400, NOW() - INTERVAL 1 MINUTE, NOW() + INTERVAL 7 DAY)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), price_points = VALUES(price_points), stock = VALUES(stock), status = 1,
    repurchase_policy = VALUES(repurchase_policy), limit_count = VALUES(limit_count),
    limit_window_seconds = VALUES(limit_window_seconds), begin_time = VALUES(begin_time), end_time = VALUES(end_time);

-- 建议压测：为每个请求生成唯一 requestId；同一 requestId 重放应返回同一 orderNo。
-- 1) 103：至少 50 个用户并发买 1 个，最终 paid/completed 数量 <= 20，stock >= 0。
-- 2) 101：同一用户并发 10 次买 1 个，成功订单数量 <= 3。
-- 3) 102：同一用户并发 10 次买 1 个，成功订单数量 = 1。
-- 4) 104：制造余额不足的支付，订单应取消且库存/限购预占恢复。
