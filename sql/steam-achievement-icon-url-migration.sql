USE game_community;
SET NAMES utf8mb4;

-- 将 MySQL 公共成就定义中的旧 Steam 图标路径统一迁移为新版地址。
-- 按路径迁移而不是按域名匹配，兼容 media、akamai、cloudflare 等历史域名。
UPDATE t_game_achievement
SET icon_url = CONCAT(
        'https://shared.akamai.steamstatic.com/community_assets/images/apps/',
        SUBSTRING_INDEX(
            SUBSTRING_INDEX(icon_url, '/steamcommunity/public/images/apps/', -1),
            '?',
            1
        )
    )
WHERE icon_url IS NOT NULL
  AND icon_url LIKE '%/steamcommunity/public/images/apps/%';
