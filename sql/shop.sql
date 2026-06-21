CREATE TABLE IF NOT EXISTS t_shop_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '商品主键',
    name VARCHAR(100) NOT NULL COMMENT '商品名称',
    description VARCHAR(500) NULL COMMENT '商品描述',
    price INT NOT NULL COMMENT '价格，金币或钻石数量',
    product_type INT NOT NULL COMMENT '商品类型：0-优惠券/权益，1-角色，2-皮肤，3-道具',
    stock INT NOT NULL DEFAULT -1 COMMENT '库存，-1 表示不限量',
    icon VARCHAR(500) NULL COMMENT '图标 URL',
    status INT NOT NULL DEFAULT 1 COMMENT '状态：0-下架，1-上架',
    business_code VARCHAR(80) NULL COMMENT '业务发放编码',
    business_id BIGINT NULL COMMENT '关联业务ID，如优惠券模板ID',
    quantity INT NOT NULL DEFAULT 1 COMMENT '购买后发放数量',
    limit_count INT NOT NULL DEFAULT -1 COMMENT '每人限购次数，-1 表示不限购',
    begin_time DATETIME NULL COMMENT '售卖开始时间',
    end_time DATETIME NULL COMMENT '售卖结束时间',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status_type (status, product_type)
) COMMENT='商城商品表';

CREATE TABLE IF NOT EXISTS t_shop_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '订单主键',
    order_no VARCHAR(40) NOT NULL COMMENT '订单号',
    request_id VARCHAR(80) NOT NULL COMMENT '前端请求幂等号',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    item_id BIGINT NOT NULL COMMENT '商品 ID',
    item_name VARCHAR(100) NOT NULL COMMENT '商品名称快照',
    item_icon VARCHAR(500) NULL COMMENT '商品图标快照',
    product_type INT NOT NULL COMMENT '商品类型快照',
    quantity INT NOT NULL DEFAULT 1 COMMENT '购买数量',
    pay_type INT NOT NULL COMMENT '支付方式：0-金币，1-钻石',
    original_price INT NOT NULL COMMENT '原总价',
    discount_amount INT NOT NULL DEFAULT 0 COMMENT '优惠金额',
    final_price INT NOT NULL COMMENT '最终支付金额',
    coupon_id BIGINT NULL COMMENT '优惠券模板ID',
    user_coupon_id BIGINT NULL COMMENT '用户优惠券ID',
    business_code VARCHAR(80) NULL COMMENT '业务发放编码快照',
    status INT NOT NULL COMMENT '状态：-1-创建失败，0-取消，1-创建中，2-待支付，3-已支付，4-已完成',
    fail_reason VARCHAR(255) NULL COMMENT '失败原因',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    pay_time DATETIME NULL,
    expire_time DATETIME NOT NULL COMMENT '支付过期时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_user_request (user_id, request_id),
    INDEX idx_user_status_time (user_id, status, create_time),
    INDEX idx_expire_status (status, expire_time)
) COMMENT='商城订单表';

SET @has_coupon_id := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 't_shop_order' AND column_name = 'coupon_id'
);
SET @ddl := IF(@has_coupon_id = 0, 'ALTER TABLE t_shop_order ADD COLUMN coupon_id BIGINT NULL COMMENT ''优惠券模板ID'' AFTER final_price', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_user_coupon_id := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 't_shop_order' AND column_name = 'user_coupon_id'
);
SET @ddl := IF(@has_user_coupon_id = 0, 'ALTER TABLE t_shop_order ADD COLUMN user_coupon_id BIGINT NULL COMMENT ''用户优惠券ID'' AFTER coupon_id', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_business_code := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 't_shop_order' AND column_name = 'business_code'
);
SET @ddl := IF(@has_business_code = 0, 'ALTER TABLE t_shop_order ADD COLUMN business_code VARCHAR(80) NULL COMMENT ''业务发放编码快照'' AFTER user_coupon_id', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_item_business_id := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 't_shop_item' AND column_name = 'business_id'
);
SET @ddl := IF(@has_item_business_id = 0, 'ALTER TABLE t_shop_item ADD COLUMN business_id BIGINT NULL COMMENT ''关联业务ID，如优惠券模板ID'' AFTER business_code', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS t_shop_user_currency (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    gold BIGINT NOT NULL DEFAULT 0 COMMENT '金币',
    diamond BIGINT NOT NULL DEFAULT 0 COMMENT '钻石',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_id (user_id)
) COMMENT='商城用户货币表';

CREATE TABLE IF NOT EXISTS t_shop_purchase_limit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    item_id BIGINT NOT NULL COMMENT '商品 ID',
    purchased_count INT NOT NULL DEFAULT 0 COMMENT '已成功占用次数',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_item (user_id, item_id)
) COMMENT='商城限购计数表';

CREATE TABLE IF NOT EXISTS t_shop_coupon (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '优惠券主键',
    name VARCHAR(100) NOT NULL COMMENT '优惠券名称',
    discount_type INT NOT NULL COMMENT '优惠类型：1-立减，2-折扣',
    discount_value INT NOT NULL COMMENT '优惠值：立减金额或折扣百分比',
    min_amount INT NOT NULL DEFAULT 0 COMMENT '最低使用金额，0表示无门槛',
    scope_type INT NOT NULL DEFAULT -1 COMMENT '兼容字段：-1全场，其他旧数据按商品类型判断',
    scope_item_id BIGINT NULL COMMENT '适用商品ID，优先级高于商品类型',
    scope_product_type INT NULL COMMENT '适用商品类型',
    expire_time DATETIME NULL COMMENT '过期时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_scope_expire (scope_type, expire_time),
    INDEX idx_scope_item (scope_item_id),
    INDEX idx_scope_product_type (scope_product_type)
) COMMENT='商城优惠券模板表';

SET @has_min_amount := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 't_shop_coupon' AND column_name = 'min_amount'
);
SET @ddl := IF(@has_min_amount = 0, 'ALTER TABLE t_shop_coupon ADD COLUMN min_amount INT NOT NULL DEFAULT 0 COMMENT ''最低使用金额，0表示无门槛'' AFTER discount_value', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_scope_item_id := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 't_shop_coupon' AND column_name = 'scope_item_id'
);
SET @ddl := IF(@has_scope_item_id = 0, 'ALTER TABLE t_shop_coupon ADD COLUMN scope_item_id BIGINT NULL COMMENT ''适用商品ID，优先级高于商品类型'' AFTER scope_type', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_scope_product_type := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 't_shop_coupon' AND column_name = 'scope_product_type'
);
SET @ddl := IF(@has_scope_product_type = 0, 'ALTER TABLE t_shop_coupon ADD COLUMN scope_product_type INT NULL COMMENT ''适用商品类型'' AFTER scope_item_id', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS t_shop_user_coupon (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户优惠券主键',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    coupon_id BIGINT NOT NULL COMMENT '优惠券模板ID',
    status INT NOT NULL DEFAULT 0 COMMENT '状态：0未使用，1已使用，2已过期，3已锁定',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    use_time DATETIME NULL COMMENT '使用时间',
    order_no VARCHAR(40) NULL COMMENT '锁定或使用的订单号',
    UNIQUE KEY uk_user_coupon (user_id, coupon_id),
    INDEX idx_user_status_time (user_id, status, create_time)
) COMMENT='商城用户优惠券表';

INSERT INTO t_shop_item (id, name, description, price, product_type, stock, icon, status, business_code, business_id, quantity, limit_count)
VALUES
(1, '星辉头像框', '个人主页专属头像框，适合活跃玩家展示身份。', 30, 3, 50, 'https://dummyimage.com/600x360/d8f7e4/24533e&text=Avatar+Frame', 1, 'avatar_frame_star', NULL, 1, 2),
(2, '限定皮肤体验卡', '可用于活动页展示的限定皮肤体验权益。', 80, 2, 20, 'https://dummyimage.com/600x360/fbe7c6/24533e&text=Skin+Pass', 1, 'skin_trial_limited', NULL, 1, 1),
(3, '社区金币礼包', '补充社区活动金币，库存不限量。', 10, 3, -1, 'https://dummyimage.com/600x360/e9f2ff/24533e&text=Gold+Pack', 1, 'community_gold_pack', NULL, 100, -1),
(4, '新人满30减5券', '购买后发放一张商城满30减5优惠券，可用于后续商品下单。', 0, 0, -1, 'https://dummyimage.com/600x360/fff0d7/24533e&text=Coupon', 1, 'coupon_template_1', 1, 1, -1)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    price = VALUES(price),
    product_type = VALUES(product_type),
    stock = VALUES(stock),
    icon = VALUES(icon),
    status = VALUES(status),
    business_code = VALUES(business_code),
    business_id = VALUES(business_id),
    quantity = VALUES(quantity),
    limit_count = VALUES(limit_count),
    update_time = CURRENT_TIMESTAMP;

INSERT INTO t_shop_user_currency (user_id, gold, diamond)
SELECT id, 1000, 500 FROM t_user
ON DUPLICATE KEY UPDATE gold = GREATEST(gold, 1000), diamond = GREATEST(diamond, 500);

INSERT INTO t_shop_coupon (id, name, discount_type, discount_value, min_amount, scope_type, scope_item_id, scope_product_type, expire_time)
VALUES
(1, '满30减5券', 1, 5, 30, -1, NULL, NULL, '2026-12-31 23:59:59'),
(2, '头像框8折券', 2, 80, 0, 1, 1, NULL, '2026-12-31 23:59:59'),
(3, '金币礼包立减2', 1, 2, 0, 3, 3, NULL, '2026-12-31 23:59:59')
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    discount_type = VALUES(discount_type),
    discount_value = VALUES(discount_value),
    min_amount = VALUES(min_amount),
    scope_type = VALUES(scope_type),
    scope_item_id = VALUES(scope_item_id),
    scope_product_type = VALUES(scope_product_type),
    expire_time = VALUES(expire_time),
    update_time = CURRENT_TIMESTAMP;

INSERT INTO t_shop_user_coupon (user_id, coupon_id, status)
SELECT u.id, c.id, 0
FROM t_user u
JOIN t_shop_coupon c ON c.id IN (1, 2, 3)
ON DUPLICATE KEY UPDATE status = IF(t_shop_user_coupon.status = 2, 0, t_shop_user_coupon.status);

INSERT INTO t_shop_purchase_limit (user_id, item_id, purchased_count, create_time, update_time)
SELECT user_id, item_id, SUM(quantity), NOW(), NOW()
FROM t_shop_order
WHERE status IN (2, 3, 4)
GROUP BY user_id, item_id
ON DUPLICATE KEY UPDATE
    purchased_count = GREATEST(t_shop_purchase_limit.purchased_count, VALUES(purchased_count)),
    update_time = CURRENT_TIMESTAMP;
