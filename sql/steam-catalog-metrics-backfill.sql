-- 将历史上只写入了占位评分的游戏标记为待拉取，交给卡片懒更新补齐 Steam 评价汇总。
USE game_community;
SET NAMES utf8mb4;

UPDATE t_game_catalog
SET metrics_synced_at = DATE_SUB(NOW(), INTERVAL 2 DAY),
    refresh_status = CASE
        WHEN refresh_status = 'READY' THEN 'BASIC_READY'
        ELSE refresh_status
    END
WHERE status = 1
  AND COALESCE(steam_review_score, 0) = 0
  AND COALESCE(steam_review_count, 0) = 0;
