CREATE TABLE IF NOT EXISTS t_game_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    account_no VARCHAR(64) NOT NULL COMMENT '对外展示的游戏账号号',
    name VARCHAR(64) NOT NULL COMMENT '游戏昵称',
    level INT NOT NULL DEFAULT 1 COMMENT '等级',
    gold BIGINT NOT NULL DEFAULT 0 COMMENT '金币',
    diamond BIGINT NOT NULL DEFAULT 0 COMMENT '钻石',
    current_season_rank VARCHAR(64) NOT NULL DEFAULT '' COMMENT '当前赛季段位',
    history_season_rank VARCHAR(64) NOT NULL DEFAULT '' COMMENT '历史最高段位',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0正常, 1封禁',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_game_account_no (account_no)
) COMMENT='游戏账号表';

CREATE TABLE IF NOT EXISTS t_user_game_bind (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '社区用户ID',
    game_account_id BIGINT NOT NULL COMMENT '游戏账号ID',
    bind_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_game_bind_user (user_id),
    UNIQUE KEY uk_user_game_bind_account (game_account_id),
    INDEX idx_game_bind_account (game_account_id)
) COMMENT='用户与游戏账号绑定表';

CREATE TABLE IF NOT EXISTS t_game_character (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    character_code VARCHAR(64) NOT NULL COMMENT '角色编码',
    name VARCHAR(64) NOT NULL COMMENT '角色名称',
    title VARCHAR(128) NOT NULL DEFAULT '' COMMENT '角色称号',
    icon VARCHAR(512) NOT NULL DEFAULT '' COMMENT '角色图标',
    rarity TINYINT NOT NULL DEFAULT 1 COMMENT '稀有度',
    element_type TINYINT NOT NULL DEFAULT 0 COMMENT '元素类型',
    character_type TINYINT NOT NULL DEFAULT 0 COMMENT '角色类型',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0禁用, 1启用',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_game_character_code (character_code),
    INDEX idx_game_character_status_sort (status, sort_order)
) COMMENT='角色元数据表';

CREATE TABLE IF NOT EXISTS t_game_skin (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    skin_code VARCHAR(64) NOT NULL COMMENT '皮肤编码',
    character_id BIGINT NULL COMMENT '所属角色ID',
    character_code VARCHAR(64) NOT NULL DEFAULT '' COMMENT '所属角色编码',
    name VARCHAR(64) NOT NULL COMMENT '皮肤名称',
    icon VARCHAR(512) NOT NULL DEFAULT '' COMMENT '皮肤图标',
    rarity TINYINT NOT NULL DEFAULT 1 COMMENT '稀有度',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0禁用, 1启用',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_game_skin_code (skin_code),
    INDEX idx_game_skin_character (character_code),
    INDEX idx_game_skin_status_sort (status, sort_order)
) COMMENT='皮肤元数据表';

CREATE TABLE IF NOT EXISTS t_game_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    item_code VARCHAR(64) NOT NULL COMMENT '道具编码',
    name VARCHAR(64) NOT NULL COMMENT '道具名称',
    item_type TINYINT NOT NULL DEFAULT 0 COMMENT '道具类型',
    rarity TINYINT NOT NULL DEFAULT 1 COMMENT '稀有度',
    icon VARCHAR(512) NOT NULL DEFAULT '' COMMENT '道具图标',
    max_stack_count INT NOT NULL DEFAULT 9999 COMMENT '最大堆叠数',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0禁用, 1启用',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_game_item_code (item_code),
    INDEX idx_game_item_status_sort (status, sort_order)
) COMMENT='道具元数据表';

CREATE TABLE IF NOT EXISTS t_account_character (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    game_account_id BIGINT NOT NULL COMMENT '游戏账号ID',
    character_id BIGINT NOT NULL COMMENT '角色ID',
    character_code VARCHAR(64) NOT NULL COMMENT '角色编码',
    level INT NOT NULL DEFAULT 1 COMMENT '角色等级',
    exp INT NOT NULL DEFAULT 0 COMMENT '角色经验',
    breakthrough_level INT NOT NULL DEFAULT 0 COMMENT '突破等级',
    obtain_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '获取时间',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0禁用, 1启用',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_account_character (game_account_id, character_code),
    INDEX idx_account_character_account (game_account_id)
) COMMENT='账号拥有角色表';

CREATE TABLE IF NOT EXISTS t_account_skin (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    game_account_id BIGINT NOT NULL COMMENT '游戏账号ID',
    skin_id BIGINT NOT NULL COMMENT '皮肤ID',
    character_id BIGINT NULL COMMENT '所属角色ID',
    skin_code VARCHAR(64) NOT NULL COMMENT '皮肤编码',
    character_code VARCHAR(64) NOT NULL DEFAULT '' COMMENT '所属角色编码',
    equip_status TINYINT NOT NULL DEFAULT 0 COMMENT '装备状态: 0未装备, 1已装备',
    obtain_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '获取时间',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0禁用, 1启用',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_account_skin (game_account_id, skin_code),
    INDEX idx_account_skin_account (game_account_id)
) COMMENT='账号拥有皮肤表';

CREATE TABLE IF NOT EXISTS t_account_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    game_account_id BIGINT NOT NULL COMMENT '游戏账号ID',
    item_id BIGINT NOT NULL COMMENT '道具ID',
    item_code VARCHAR(64) NOT NULL COMMENT '道具编码',
    quantity INT NOT NULL DEFAULT 0 COMMENT '当前数量',
    last_obtain_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后一次获得时间',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0禁用, 1启用',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_account_item (game_account_id, item_code),
    INDEX idx_account_item_account (game_account_id)
) COMMENT='账号拥有道具表';

CREATE TABLE IF NOT EXISTS t_sign_in_reward (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    day_index INT NOT NULL COMMENT '签到天数序号',
    reward_type TINYINT NOT NULL COMMENT '奖励类型: 1角色, 2皮肤, 3道具, 4金币, 5钻石',
    business_code VARCHAR(64) NOT NULL DEFAULT '' COMMENT '奖励业务编码',
    reward_code VARCHAR(64) NOT NULL DEFAULT '' COMMENT '奖励资源编码',
    quantity INT NOT NULL DEFAULT 1 COMMENT '奖励数量',
    reward_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT '奖励展示名称',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0禁用, 1启用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sign_in_reward_day (day_index)
) COMMENT='签到奖励配置表';

CREATE TABLE IF NOT EXISTS t_sign_in_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    game_account_id BIGINT NOT NULL COMMENT '游戏账号ID',
    month_key CHAR(7) NOT NULL COMMENT '月份, 例如 2026-05',
    sign_bits BIGINT NOT NULL DEFAULT 0 COMMENT '按位记录每日签到状态',
    sign_count INT NOT NULL DEFAULT 0 COMMENT '当月签到次数',
    consecutive_days INT NOT NULL DEFAULT 0 COMMENT '连续签到天数',
    last_sign_in_date DATETIME NULL COMMENT '最近一次签到时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sign_in_record_month (game_account_id, month_key)
) COMMENT='月度签到状态表';

CREATE TABLE IF NOT EXISTS t_game_delivery_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    order_no VARCHAR(64) NOT NULL COMMENT '商城订单号',
    user_id BIGINT NOT NULL COMMENT '社区用户ID',
    game_account_id BIGINT NULL COMMENT '发货时解析到的游戏账号ID',
    product_type INT NOT NULL COMMENT '商品类型',
    business_code VARCHAR(64) NOT NULL DEFAULT '' COMMENT '业务编码',
    business_id BIGINT NULL COMMENT '业务ID',
    quantity INT NOT NULL DEFAULT 1 COMMENT '发放数量',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0待处理, 1成功, 2失败',
    fail_reason VARCHAR(255) NOT NULL DEFAULT '' COMMENT '失败原因',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_game_delivery_order (order_no),
    INDEX idx_game_delivery_user_status (user_id, status),
    INDEX idx_game_delivery_account_status (game_account_id, status)
) COMMENT='游戏资源发货幂等表';

INSERT INTO t_game_account (id, account_no, name, level, gold, diamond, current_season_rank, history_season_rank, status, version)
VALUES
(1, 'GA10001', '星野', 28, 12000, 1800, '钻石 III', '星耀 V', 0, 0),
(2, 'GA10002', '暮光', 34, 22600, 2600, '星耀 II', '王者 12 星', 0, 0),
(3, 'GA10003', '雾切', 18, 7600, 920, '铂金 I', '钻石 V', 0, 0),
(4, 'GA10004', '霜刃', 41, 30800, 5200, '王者 18 星', '荣耀王者', 0, 0)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    level = VALUES(level),
    gold = VALUES(gold),
    diamond = VALUES(diamond),
    current_season_rank = VALUES(current_season_rank),
    history_season_rank = VALUES(history_season_rank),
    status = VALUES(status),
    update_time = CURRENT_TIMESTAMP;

INSERT INTO t_game_character (id, character_code, name, title, icon, rarity, element_type, character_type, status, sort_order, version)
VALUES
(1, 'char_blade', '刃行者', '裂风先锋', 'https://dummyimage.com/400x400/d6eef0/24533e&text=Blade', 3, 1, 1, 1, 10, 0),
(2, 'char_oracle', '星谕', '月海观测者', 'https://dummyimage.com/400x400/f6ead9/24533e&text=Oracle', 4, 2, 2, 1, 20, 0),
(3, 'char_vanguard', '玄垒', '黑曜壁垒', 'https://dummyimage.com/400x400/e5e8ff/24533e&text=Vanguard', 2, 3, 3, 1, 30, 0)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    title = VALUES(title),
    icon = VALUES(icon),
    rarity = VALUES(rarity),
    element_type = VALUES(element_type),
    character_type = VALUES(character_type),
    status = VALUES(status),
    sort_order = VALUES(sort_order),
    update_time = CURRENT_TIMESTAMP;

INSERT INTO t_game_skin (id, skin_code, character_id, character_code, name, icon, rarity, status, sort_order, version)
VALUES
(1, 'skin_blade_night', 1, 'char_blade', '夜巡刃影', 'https://dummyimage.com/400x400/1f2937/f9fafb&text=Night+Blade', 3, 1, 10, 0),
(2, 'skin_oracle_tide', 2, 'char_oracle', '潮声星梦', 'https://dummyimage.com/400x400/0ea5e9/f8fafc&text=Tide+Oracle', 4, 1, 20, 0),
(3, 'skin_vanguard_core', 3, 'char_vanguard', '熔核壁垒', 'https://dummyimage.com/400x400/f97316/fff7ed&text=Core+Vanguard', 2, 1, 30, 0)
ON DUPLICATE KEY UPDATE
    character_id = VALUES(character_id),
    character_code = VALUES(character_code),
    name = VALUES(name),
    icon = VALUES(icon),
    rarity = VALUES(rarity),
    status = VALUES(status),
    sort_order = VALUES(sort_order),
    update_time = CURRENT_TIMESTAMP;

INSERT INTO t_game_item (id, item_code, name, item_type, rarity, icon, max_stack_count, status, sort_order, version)
VALUES
(1, 'item_gold_pack', '金币补给箱', 1, 1, 'https://dummyimage.com/400x400/fef3c7/92400e&text=Gold+Pack', 9999, 1, 10, 0),
(2, 'item_rename_card', '改名凭证', 2, 2, 'https://dummyimage.com/400x400/d1fae5/065f46&text=Rename', 99, 1, 20, 0),
(3, 'item_skin_ticket', '皮肤体验券', 3, 3, 'https://dummyimage.com/400x400/e9d5ff/5b21b6&text=Ticket', 999, 1, 30, 0)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    item_type = VALUES(item_type),
    rarity = VALUES(rarity),
    icon = VALUES(icon),
    max_stack_count = VALUES(max_stack_count),
    status = VALUES(status),
    sort_order = VALUES(sort_order),
    update_time = CURRENT_TIMESTAMP;

INSERT INTO t_sign_in_reward (id, day_index, reward_type, business_code, reward_code, quantity, reward_name, status)
VALUES
(1, 1, 4, 'gold_reward', 'gold', 100, '金币 x100', 1),
(2, 2, 4, 'gold_reward', 'gold', 200, '金币 x200', 1),
(3, 3, 3, 'item_reward', 'item_skin_ticket', 1, '皮肤体验券 x1', 1),
(4, 4, 5, 'diamond_reward', 'diamond', 20, '钻石 x20', 1),
(5, 5, 4, 'gold_reward', 'gold', 500, '金币 x500', 1),
(6, 6, 3, 'item_reward', 'item_rename_card', 1, '改名凭证 x1', 1),
(7, 7, 2, 'skin_reward', 'skin_blade_night', 1, '夜巡刃影 x1', 1)
ON DUPLICATE KEY UPDATE
    reward_type = VALUES(reward_type),
    business_code = VALUES(business_code),
    reward_code = VALUES(reward_code),
    quantity = VALUES(quantity),
    reward_name = VALUES(reward_name),
    status = VALUES(status),
    update_time = CURRENT_TIMESTAMP;
