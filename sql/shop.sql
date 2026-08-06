-- 积分装扮商城 v2（不兼容旧版，可整库替换 shop 相关表）
USE game_community;
SET NAMES utf8mb4;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS t_shop_points_ledger;
DROP TABLE IF EXISTS t_shop_user_coupon;
DROP TABLE IF EXISTS t_shop_coupon;
DROP TABLE IF EXISTS t_shop_order;
DROP TABLE IF EXISTS t_shop_purchase_limit;
DROP TABLE IF EXISTS t_shop_user_currency;
DROP TABLE IF EXISTS t_shop_item;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE t_shop_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    cosmetic_code VARCHAR(64) NOT NULL COMMENT '关联 t_cosmetic_def.code',
    price_points INT NOT NULL COMMENT '积分单价',
    grant_quantity INT NOT NULL DEFAULT 1 COMMENT '每次购买发放装扮数量',
    stock INT NOT NULL DEFAULT -1 COMMENT '-1 不限量',
    icon VARCHAR(500) NULL,
    status INT NOT NULL DEFAULT 1 COMMENT '0下架 1上架',
    repurchase_policy VARCHAR(32) NOT NULL DEFAULT 'ONCE_FOREVER'
        COMMENT 'ONCE_FOREVER/UNLIMITED/COOLDOWN/LIMIT_PER_WINDOW',
    limit_count INT NOT NULL DEFAULT -1 COMMENT '限购次数，-1 不限',
    limit_window_seconds INT NULL COMMENT 'CD 或窗口秒数',
    begin_time DATETIME NULL,
    end_time DATETIME NULL,
    version INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_shop_item_status (status, begin_time, end_time),
    KEY idx_shop_item_cosmetic (cosmetic_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分商城商品';

CREATE TABLE t_shop_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(40) NOT NULL,
    request_id VARCHAR(80) NOT NULL,
    user_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    item_name VARCHAR(100) NOT NULL,
    item_icon VARCHAR(500) NULL,
    cosmetic_code VARCHAR(64) NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    price_points INT NOT NULL COMMENT '单价快照',
    total_points INT NOT NULL COMMENT '实付积分',
    grant_quantity INT NOT NULL DEFAULT 1 COMMENT '单次发放数量快照',
    status INT NOT NULL COMMENT '-1失败 0取消 1创建中 2待支付 3已支付 4已完成',
    fail_reason VARCHAR(255) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    pay_time DATETIME NULL,
    complete_time DATETIME NULL,
    expire_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_user_request (user_id, request_id),
    KEY idx_user_status_time (user_id, status, create_time),
    KEY idx_expire_status (status, expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分商城订单';

CREATE TABLE t_shop_user_currency (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    points BIGINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户积分账户';

CREATE TABLE t_shop_purchase_limit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    purchased_count INT NOT NULL DEFAULT 0,
    last_purchase_at DATETIME NULL,
    window_start_at DATETIME NULL COMMENT 'LIMIT_PER_WINDOW 窗口起点',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_item (user_id, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='限购计数';

CREATE TABLE t_shop_points_ledger (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    delta BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    biz_type VARCHAR(32) NOT NULL COMMENT 'SHOP_EXCHANGE/ADMIN_ADJUST/...',
    biz_ref VARCHAR(64) NULL,
    remark VARCHAR(255) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_ledger_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分流水';

INSERT INTO t_shop_item (id, name, description, cosmetic_code, price_points, grant_quantity, stock, icon, status,
                         repurchase_policy, limit_count, limit_window_seconds)
VALUES
(1, '星辉头像框', '兑换后可装备星辉头像框', 'avatar_frame_star', 300, 1, 50,
 'https://dummyimage.com/600x360/d8f7e4/24533e&text=Avatar+Frame', 1, 'ONCE_FOREVER', 1, NULL),
(2, '落日主页背景', '兑换后可装备个人主页背景', 'profile_bg_sunset', 500, 1, -1,
 'https://dummyimage.com/600x360/fbe7c6/24533e&text=Profile+BG', 1, 'ONCE_FOREVER', 1, NULL),
(3, '霓虹评论卡片', '兑换后可装备评论卡片样式', 'comment_card_neon', 200, 1, 100,
 'https://dummyimage.com/600x360/e9f2ff/24533e&text=Comment+Card', 1, 'ONCE_FOREVER', 1, NULL)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    cosmetic_code = VALUES(cosmetic_code),
    price_points = VALUES(price_points),
    grant_quantity = VALUES(grant_quantity),
    stock = VALUES(stock),
    icon = VALUES(icon),
    status = VALUES(status),
    repurchase_policy = VALUES(repurchase_policy),
    limit_count = VALUES(limit_count),
    limit_window_seconds = VALUES(limit_window_seconds),
    update_time = CURRENT_TIMESTAMP;

INSERT INTO t_shop_user_currency (user_id, points)
SELECT id, 5000 FROM t_user
ON DUPLICATE KEY UPDATE points = GREATEST(points, 5000);
