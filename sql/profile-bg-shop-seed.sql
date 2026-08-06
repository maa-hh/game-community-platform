-- 9 款主页背景装扮 + 积分商城上架（执行前请确保 cosmetic / shop 表已存在）
USE game_community;
SET NAMES utf8mb4;

INSERT INTO t_cosmetic_def (code, name, category, effect_mode, slot, preview_url, asset_json, status)
VALUES
('profile_bg_hornet_soft', '丝之拥', 'PROFILE_BG', 'EQUIP', 'PROFILE_BG',
 '/cosmetic/profile-bg/hornet-soft.png',
 '{"bgImage":"/cosmetic/profile-bg/hornet-soft.png","overlayGradient":"to bottom, rgba(255,255,255,0.25) 0%, rgba(255,255,255,0.72) 100%","textTheme":"dark"}', 1),
('profile_bg_hornet_fluffy', '绒毛小憩', 'PROFILE_BG', 'EQUIP', 'PROFILE_BG',
 '/cosmetic/profile-bg/hornet-fluffy.png',
 '{"bgImage":"/cosmetic/profile-bg/hornet-fluffy.png","overlayGradient":"to bottom, rgba(255,255,255,0.2) 0%, rgba(255,240,245,0.78) 100%","textTheme":"dark"}', 1),
('profile_bg_hollow_mask', '破碎面具', 'PROFILE_BG', 'EQUIP', 'PROFILE_BG',
 '/cosmetic/profile-bg/hollow-mask.png',
 '{"bgImage":"/cosmetic/profile-bg/hollow-mask.png","overlayGradient":"to bottom, rgba(0,0,0,0.15) 0%, rgba(0,0,0,0.78) 100%","textTheme":"light"}', 1),
('profile_bg_radiance_void', '辉光降临', 'PROFILE_BG', 'EQUIP', 'PROFILE_BG',
 '/cosmetic/profile-bg/radiance-void.png',
 '{"bgImage":"/cosmetic/profile-bg/radiance-void.png","overlayGradient":"to bottom, rgba(0,0,0,0.2) 0%, rgba(0,0,0,0.85) 100%","textTheme":"light"}', 1),
('profile_bg_silver_uniform', '银发校服', 'PROFILE_BG', 'EQUIP', 'PROFILE_BG',
 '/cosmetic/profile-bg/silver-uniform.png',
 '{"bgImage":"/cosmetic/profile-bg/silver-uniform.png","overlayGradient":"to bottom, rgba(255,255,255,0.35) 0%, rgba(245,248,252,0.88) 100%","textTheme":"dark"}', 1),
('profile_bg_summer_sweet', '夏日甜筒', 'PROFILE_BG', 'EQUIP', 'PROFILE_BG',
 '/cosmetic/profile-bg/summer-sweet.png',
 '{"bgImage":"/cosmetic/profile-bg/summer-sweet.png","overlayGradient":"to bottom, rgba(255,255,255,0.18) 0%, rgba(255,248,240,0.75) 100%","textTheme":"dark"}', 1),
('profile_bg_angel_city', '都市天使', 'PROFILE_BG', 'EQUIP', 'PROFILE_BG',
 '/cosmetic/profile-bg/angel-city.png',
 '{"bgImage":"/cosmetic/profile-bg/angel-city.png","overlayGradient":"to bottom, rgba(0,0,0,0.2) 0%, rgba(20,0,0,0.82) 100%","textTheme":"light"}', 1),
('profile_bg_angel_melancholy', '融化的忧伤', 'PROFILE_BG', 'EQUIP', 'PROFILE_BG',
 '/cosmetic/profile-bg/angel-melancholy.png',
 '{"bgImage":"/cosmetic/profile-bg/angel-melancholy.png","overlayGradient":"to bottom, rgba(255,255,255,0.3) 0%, rgba(250,250,250,0.9) 100%","textTheme":"dark"}', 1),
('profile_bg_sakura_dream', '樱粉花梦', 'PROFILE_BG', 'EQUIP', 'PROFILE_BG',
 '/cosmetic/profile-bg/sakura-dream.png',
 '{"bgImage":"/cosmetic/profile-bg/sakura-dream.png","overlayGradient":"to bottom, rgba(0,0,0,0.18) 0%, rgba(30,10,30,0.8) 100%","textTheme":"light"}', 1)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    category = VALUES(category),
    effect_mode = VALUES(effect_mode),
    slot = VALUES(slot),
    preview_url = VALUES(preview_url),
    asset_json = VALUES(asset_json),
    status = VALUES(status),
    update_time = CURRENT_TIMESTAMP;

INSERT INTO t_shop_item (id, name, description, cosmetic_code, price_points, grant_quantity, stock, icon, status,
                         repurchase_policy, limit_count, limit_window_seconds)
VALUES
(4, '丝之拥', '兑换后可在个人主页装备，访客也能看到你的主页背景', 'profile_bg_hornet_soft', 380, 1, -1,
 '/cosmetic/profile-bg/hornet-soft.png', 1, 'ONCE_FOREVER', 1, NULL),
(5, '绒毛小憩', '兑换后可在个人主页装备，访客也能看到你的主页背景', 'profile_bg_hornet_fluffy', 420, 1, -1,
 '/cosmetic/profile-bg/hornet-fluffy.png', 1, 'ONCE_FOREVER', 1, NULL),
(6, '破碎面具', '兑换后可在个人主页装备，访客也能看到你的主页背景', 'profile_bg_hollow_mask', 520, 1, -1,
 '/cosmetic/profile-bg/hollow-mask.png', 1, 'ONCE_FOREVER', 1, NULL),
(7, '辉光降临', '兑换后可在个人主页装备，访客也能看到你的主页背景', 'profile_bg_radiance_void', 680, 1, -1,
 '/cosmetic/profile-bg/radiance-void.png', 1, 'ONCE_FOREVER', 1, NULL),
(8, '银发校服', '兑换后可在个人主页装备，访客也能看到你的主页背景', 'profile_bg_silver_uniform', 360, 1, -1,
 '/cosmetic/profile-bg/silver-uniform.png', 1, 'ONCE_FOREVER', 1, NULL),
(9, '夏日甜筒', '兑换后可在个人主页装备，访客也能看到你的主页背景', 'profile_bg_summer_sweet', 450, 1, -1,
 '/cosmetic/profile-bg/summer-sweet.png', 1, 'ONCE_FOREVER', 1, NULL),
(10, '都市天使', '兑换后可在个人主页装备，访客也能看到你的主页背景', 'profile_bg_angel_city', 580, 1, -1,
 '/cosmetic/profile-bg/angel-city.png', 1, 'ONCE_FOREVER', 1, NULL),
(11, '融化的忧伤', '兑换后可在个人主页装备，访客也能看到你的主页背景', 'profile_bg_angel_melancholy', 400, 1, -1,
 '/cosmetic/profile-bg/angel-melancholy.png', 1, 'ONCE_FOREVER', 1, NULL),
(12, '樱粉花梦', '兑换后可在个人主页装备，访客也能看到你的主页背景', 'profile_bg_sakura_dream', 620, 1, -1,
 '/cosmetic/profile-bg/sakura-dream.png', 1, 'ONCE_FOREVER', 1, NULL)
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
