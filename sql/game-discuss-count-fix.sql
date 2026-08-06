-- 回填游戏讨论数（与 t_article_game 已发布帖子对齐）
USE game_community;
SET NAMES utf8mb4;

UPDATE t_game_catalog c
SET c.discuss_count = (
    SELECT COUNT(*)
    FROM t_article_game ag
    INNER JOIN t_article a ON a.id = ag.article_id
    WHERE ag.game_app_id = c.app_id
      AND a.status = 1
      AND a.deleted = 0
),
c.update_time = NOW();
