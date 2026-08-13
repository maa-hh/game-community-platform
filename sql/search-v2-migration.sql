SET NAMES utf8mb4;

-- 搜索建议词 v2 数据迁移：只保留文章标题、AI 和游戏来源。
-- 先移除旧分词/分区来源，再按现存关系回填主表聚合来源，最后淘汰没有有效来源的孤立词。
START TRANSACTION;

DELETE FROM t_suggest_term_source
WHERE source_type NOT IN ('ARTICLE', 'AI', 'GAME', 'UPLOAD');

UPDATE t_suggest_term term
JOIN (
    SELECT source.term_id,
           CASE MAX(CASE source.source_type
                        WHEN 'UPLOAD' THEN 4
                        WHEN 'GAME' THEN 3
                        WHEN 'AI' THEN 2
                        ELSE 1
                    END)
               WHEN 4 THEN 'UPLOAD'
               WHEN 3 THEN 'GAME'
               WHEN 2 THEN 'AI'
               ELSE 'ARTICLE'
           END AS source_type,
           CAST(SUBSTRING_INDEX(
               GROUP_CONCAT(
                   source.source_article_id
                   ORDER BY CASE source.source_type
                                WHEN 'UPLOAD' THEN 4
                                WHEN 'GAME' THEN 3
                                WHEN 'AI' THEN 2
                                ELSE 1
                            END DESC,
                            source.source_article_id ASC
                   SEPARATOR ','
               ), ',', 1
           ) AS UNSIGNED) AS source_id
    FROM t_suggest_term_source source
    GROUP BY source.term_id
) live ON live.term_id = term.id
SET term.source_type = live.source_type,
    term.source_article_id = COALESCE(live.source_id, 0),
    term.status = 'ACTIVE',
    term.updated_at = CURRENT_TIMESTAMP
WHERE term.status <> 'DISABLED';

UPDATE t_suggest_term term
LEFT JOIN (
    SELECT DISTINCT term_id
    FROM t_suggest_term_source
) live ON live.term_id = term.id
SET term.status = 'EXPIRED',
    term.updated_at = CURRENT_TIMESTAMP
WHERE term.status = 'ACTIVE'
  AND live.term_id IS NULL;

COMMIT;
