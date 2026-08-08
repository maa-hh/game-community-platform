-- 社区装扮（user-service）
USE game_community;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS t_cosmetic_def (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    code VARCHAR(64) NOT NULL COMMENT '装扮编码',
    name VARCHAR(100) NOT NULL COMMENT '名称',
    category VARCHAR(32) NOT NULL COMMENT 'AVATAR_FRAME/COMMENT_CARD/COMMENT_FONT/POST_CARD/PROFILE_BG',
    effect_mode VARCHAR(16) NOT NULL COMMENT 'EQUIP/CONSUMABLE',
    slot VARCHAR(32) NULL COMMENT '装备槽位，EQUIP 必填',
    preview_url VARCHAR(512) NULL COMMENT '预览图',
    asset_json JSON NOT NULL COMMENT '样式资源 JSON',
    consumable_config JSON NULL COMMENT '消耗品配置',
    default_duration_seconds INT NULL COMMENT '默认持续秒数',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0下架 1上架',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_cosmetic_code (code),
    KEY idx_cosmetic_category (category, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='装扮目录';

CREATE TABLE IF NOT EXISTS t_user_cosmetic (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    cosmetic_code VARCHAR(64) NOT NULL,
    quantity INT NOT NULL DEFAULT 1 COMMENT 'EQUIP 固定1，CONSUMABLE 可堆叠',
    source_type VARCHAR(16) NOT NULL DEFAULT 'SHOP',
    source_ref VARCHAR(64) NULL COMMENT '订单号等',
    acquired_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_at DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_cosmetic (user_id, cosmetic_code),
    KEY idx_user_cosmetic_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户装扮背包';

CREATE TABLE IF NOT EXISTS t_user_cosmetic_loadout (
    user_id BIGINT PRIMARY KEY,
    avatar_frame_code VARCHAR(64) NULL,
    comment_card_code VARCHAR(64) NULL,
    comment_font_code VARCHAR(64) NULL,
    post_card_code VARCHAR(64) NULL,
    profile_bg_code VARCHAR(64) NULL,
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户当前装备';

CREATE TABLE IF NOT EXISTS t_user_cosmetic_use_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    cosmetic_code VARCHAR(64) NOT NULL,
    use_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    biz_ref VARCHAR(64) NULL,
    payload_json JSON NULL,
    KEY idx_use_log_user_time (user_id, use_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消耗品使用记录';

CREATE TABLE IF NOT EXISTS t_user_active_effect (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    effect_code VARCHAR(64) NOT NULL,
    source_cosmetic_code VARCHAR(64) NOT NULL,
    start_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_at DATETIME NULL,
    payload_json JSON NULL,
    KEY idx_active_effect_user_start (user_id, start_at, expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='进行中的临时效果';

CREATE TABLE IF NOT EXISTS t_cosmetic_grant_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    cosmetic_code VARCHAR(64) NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_grant_order (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='装扮发放幂等';

INSERT INTO t_cosmetic_def (code, name, category, effect_mode, slot, preview_url, asset_json, status)
VALUES
('avatar_frame_star', '星辉头像框', 'AVATAR_FRAME', 'EQUIP', 'AVATAR_FRAME',
 'https://dummyimage.com/120x120/d8f7e4/24533e&text=Frame',
 '{"frameUrl":"https://dummyimage.com/120x120/d8f7e4/24533e&text=Frame","padding":4}', 1),
('profile_bg_sunset', '落日主页背景', 'PROFILE_BG', 'EQUIP', 'PROFILE_BG',
 'https://dummyimage.com/600x200/fbe7c6/24533e&text=BG',
 '{"bgImage":"https://dummyimage.com/600x200/fbe7c6/24533e&text=BG","overlay":"rgba(0,0,0,0.2)"}', 1),
('comment_card_neon', '霓虹评论卡片', 'COMMENT_CARD', 'EQUIP', 'COMMENT_CARD',
 'https://dummyimage.com/400x120/e9f2ff/24533e&text=Card',
 '{"bg":"#1a1a2e","border":"1px solid #00d4ff","radius":12}', 1)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    category = VALUES(category),
    effect_mode = VALUES(effect_mode),
    slot = VALUES(slot),
    preview_url = VALUES(preview_url),
    asset_json = VALUES(asset_json),
    status = VALUES(status),
    update_time = CURRENT_TIMESTAMP;
