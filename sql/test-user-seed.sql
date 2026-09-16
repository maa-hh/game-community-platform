-- ============================================================
-- 测试账号种子数据（用于双账号联调：点赞/评论/通知等）
--
-- 登录凭证：
--   邮箱：test2@test.com
--   密码：Test123456
--
-- 账号信息：
--   user_id   = 2
--   account_id = 10001
--   昵称      = TestPlayer
-- ============================================================

USE game_community;

SET @test_user_id := 2;
SET @test_account_id := 10001;
SET @test_email := 'test2@test.com';
SET @test_username := 'TestPlayer';
SET @test_password_hash := '$2y$10$/IaYnaaLW9nLarvvAAqqZOdzHMtRCwB2rDUcu50GU1xz0RfZsKX1S';

-- 清理旧数据（若曾手动插入半成品账号）
DELETE FROM t_notification_message WHERE user_id = @test_user_id;
DELETE FROM t_notification_user_state WHERE user_id = @test_user_id;
DELETE FROM t_shop_user_currency WHERE user_id = @test_user_id;
DELETE FROM t_user_operation_log WHERE user_id = @test_user_id;
DELETE FROM t_user_audit_reject_log WHERE user_id = @test_user_id;
DELETE FROM t_user_audit_task WHERE user_id = @test_user_id;
DELETE FROM t_user_auth WHERE user_id = @test_user_id;
DELETE FROM t_user_account WHERE user_id = @test_user_id;
DELETE FROM t_user_profile_audit WHERE user_id = @test_user_id;
DELETE FROM t_user WHERE id = @test_user_id;

UPDATE t_account_id_pool
SET status = 0, user_id = NULL, update_time = NOW()
WHERE user_id = @test_user_id OR account_id = @test_account_id;

-- 用户资料
INSERT INTO t_user (
    id, account_id, username, email, avatar, signature, steam_account,
    version, create_time, update_time, deleted
) VALUES (
    @test_user_id, @test_account_id, @test_username, @test_email, '', '这是测试账号，用于联调通知与社交功能',
    '', 0, NOW(), NOW(), 0
);

-- 资料审核状态（注册后默认全空闲）
INSERT INTO t_user_profile_audit (
    user_id, username_audit_status, signature_audit_status, avatar_audit_status,
    pending_username, pending_signature, pending_avatar,
    version, create_time, update_time
) VALUES (
    @test_user_id, 0, 0, 0, '', '', '', 0, NOW(), NOW()
);

-- 账户状态
INSERT INTO t_user_account (
    user_id, status, type, ban_reason, register_source,
    last_login_ip, version, create_time, update_time, deleted
) VALUES (
    @test_user_id, 0, 0, '', 'EMAIL', '', 0, NOW(), NOW(), 0
);

-- 登录凭证（BCrypt: Test123456）
INSERT INTO t_user_auth (
    user_id, password, fail_count, version, create_time, update_time, deleted
) VALUES (
    @test_user_id, @test_password_hash, 0, 0, NOW(), NOW(), 0
);

-- 占用账号号池
UPDATE t_account_id_pool
SET status = 1, user_id = @test_user_id, update_time = NOW()
WHERE account_id = @test_account_id;

-- 商城货币（与 shop.sql 种子策略一致）
INSERT INTO t_shop_user_currency (user_id, points)
VALUES (@test_user_id, 100000)
ON DUPLICATE KEY UPDATE
    points = GREATEST(points, 100000),
    update_time = NOW();

-- 通知用户状态（初始无未读）
INSERT INTO t_notification_user_state (
    user_id, unread_notification_count, feed_unread_flag, feed_unread_count,
    last_feed_event_time, last_feed_read_time, update_time
) VALUES (
    @test_user_id, 0, 0, 0, NULL, NULL, NOW()
)
ON DUPLICATE KEY UPDATE
    unread_notification_count = 0,
    feed_unread_flag = 0,
    feed_unread_count = 0,
    last_feed_event_time = NULL,
    last_feed_read_time = NULL,
    update_time = NOW();
