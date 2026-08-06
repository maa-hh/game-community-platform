-- 9 款头像挂件 + 积分商城上架
USE game_community;
SET NAMES utf8mb4;

UPDATE t_cosmetic_def
SET
    name = '鎏金戒环',
    preview_url = '/cosmetic/avatar-frame/golden-ring.svg',
    asset_json = '{"frameUrl":"/cosmetic/avatar-frame/golden-ring.svg","scale":1.48}',
    status = 1,
    update_time = CURRENT_TIMESTAMP
WHERE code = 'avatar_frame_star';

INSERT INTO t_cosmetic_def (code, name, category, effect_mode, slot, preview_url, asset_json, status)
VALUES
('avatar_frame_golden_ring', '鎏金戒环', 'AVATAR_FRAME', 'EQUIP', 'AVATAR_FRAME',
 '/cosmetic/avatar-frame/golden-ring.svg',
 '{"frameUrl":"/cosmetic/avatar-frame/golden-ring.svg","scale":1.48}', 1),
('avatar_frame_royal_crown', '精灵王冠', 'AVATAR_FRAME', 'EQUIP', 'AVATAR_FRAME',
 '/cosmetic/avatar-frame/royal-crown.svg',
 '{"frameUrl":"/cosmetic/avatar-frame/royal-crown.svg","scale":1.5}', 1),
('avatar_frame_naval_blue', '碧海舰徽', 'AVATAR_FRAME', 'EQUIP', 'AVATAR_FRAME',
 '/cosmetic/avatar-frame/naval-blue.svg',
 '{"frameUrl":"/cosmetic/avatar-frame/naval-blue.svg","scale":1.48}', 1),
('avatar_frame_shadow_lotus', '幽莲夜冠', 'AVATAR_FRAME', 'EQUIP', 'AVATAR_FRAME',
 '/cosmetic/avatar-frame/shadow-lotus.svg',
 '{"frameUrl":"/cosmetic/avatar-frame/shadow-lotus.svg","scale":1.5}', 1),
('avatar_frame_emerald_serpent', '翡翠蛇环', 'AVATAR_FRAME', 'EQUIP', 'AVATAR_FRAME',
 '/cosmetic/avatar-frame/emerald-serpent.svg',
 '{"frameUrl":"/cosmetic/avatar-frame/emerald-serpent.svg","scale":1.48}', 1),
('avatar_frame_sky_wings', '苍穹翼徽', 'AVATAR_FRAME', 'EQUIP', 'AVATAR_FRAME',
 '/cosmetic/avatar-frame/sky-wings.svg',
 '{"frameUrl":"/cosmetic/avatar-frame/sky-wings.svg","scale":1.52}', 1),
('avatar_frame_amber_leaf', '琥珀叶环', 'AVATAR_FRAME', 'EQUIP', 'AVATAR_FRAME',
 '/cosmetic/avatar-frame/amber-leaf.svg',
 '{"frameUrl":"/cosmetic/avatar-frame/amber-leaf.svg","scale":1.48}', 1),
('avatar_frame_blaze_lion', '炽焰狮心', 'AVATAR_FRAME', 'EQUIP', 'AVATAR_FRAME',
 '/cosmetic/avatar-frame/blaze-lion.svg',
 '{"frameUrl":"/cosmetic/avatar-frame/blaze-lion.svg","scale":1.5}', 1),
('avatar_frame_steel_glory', '钢铁荣耀', 'AVATAR_FRAME', 'EQUIP', 'AVATAR_FRAME',
 '/cosmetic/avatar-frame/steel-glory.svg',
 '{"frameUrl":"/cosmetic/avatar-frame/steel-glory.svg","scale":1.48}', 1)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    category = VALUES(category),
    effect_mode = VALUES(effect_mode),
    slot = VALUES(slot),
    preview_url = VALUES(preview_url),
    asset_json = VALUES(asset_json),
    status = VALUES(status),
    update_time = CURRENT_TIMESTAMP;

UPDATE t_shop_item
SET
    name = '鎏金戒环',
    description = '兑换后装备头像挂件，全站头像旁展示同款效果',
    cosmetic_code = 'avatar_frame_golden_ring',
    icon = '/cosmetic/avatar-frame/golden-ring.svg',
    price_points = 280,
    update_time = CURRENT_TIMESTAMP
WHERE id = 1;

INSERT INTO t_shop_item (id, name, description, cosmetic_code, price_points, grant_quantity, stock, icon, status,
                         repurchase_policy, limit_count, limit_window_seconds)
VALUES
(13, '精灵王冠', '兑换后装备头像挂件，全站头像旁展示同款效果', 'avatar_frame_royal_crown', 320, 1, -1,
 '/cosmetic/avatar-frame/royal-crown.svg', 1, 'ONCE_FOREVER', 1, NULL),
(14, '碧海舰徽', '兑换后装备头像挂件，全站头像旁展示同款效果', 'avatar_frame_naval_blue', 300, 1, -1,
 '/cosmetic/avatar-frame/naval-blue.svg', 1, 'ONCE_FOREVER', 1, NULL),
(15, '幽莲夜冠', '兑换后装备头像挂件，全站头像旁展示同款效果', 'avatar_frame_shadow_lotus', 340, 1, -1,
 '/cosmetic/avatar-frame/shadow-lotus.svg', 1, 'ONCE_FOREVER', 1, NULL),
(16, '翡翠蛇环', '兑换后装备头像挂件，全站头像旁展示同款效果', 'avatar_frame_emerald_serpent', 360, 1, -1,
 '/cosmetic/avatar-frame/emerald-serpent.svg', 1, 'ONCE_FOREVER', 1, NULL),
(17, '苍穹翼徽', '兑换后装备头像挂件，全站头像旁展示同款效果', 'avatar_frame_sky_wings', 380, 1, -1,
 '/cosmetic/avatar-frame/sky-wings.svg', 1, 'ONCE_FOREVER', 1, NULL),
(18, '琥珀叶环', '兑换后装备头像挂件，全站头像旁展示同款效果', 'avatar_frame_amber_leaf', 300, 1, -1,
 '/cosmetic/avatar-frame/amber-leaf.svg', 1, 'ONCE_FOREVER', 1, NULL),
(19, '炽焰狮心', '兑换后装备头像挂件，全站头像旁展示同款效果', 'avatar_frame_blaze_lion', 400, 1, -1,
 '/cosmetic/avatar-frame/blaze-lion.svg', 1, 'ONCE_FOREVER', 1, NULL),
(20, '钢铁荣耀', '兑换后装备头像挂件，全站头像旁展示同款效果', 'avatar_frame_steel_glory', 420, 1, -1,
 '/cosmetic/avatar-frame/steel-glory.svg', 1, 'ONCE_FOREVER', 1, NULL)
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
