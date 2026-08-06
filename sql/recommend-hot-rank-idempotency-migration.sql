-- 行为事件幂等升级。
-- 生产执行前请确认历史数据已备份；event_id 由历史行 ID 生成，保证旧数据不重复。
-- 该迁移兼容旧表：旧表可能已存在同名普通索引，迁移会将其升级为唯一索引。
SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 't_article_behavior_event'
      AND column_name = 'event_id'
);
SET @sql := IF(
    @column_exists = 0,
    'ALTER TABLE t_article_behavior_event ADD COLUMN event_id VARCHAR(64) NULL COMMENT ''全局幂等事件ID'' AFTER id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE t_article_behavior_event
SET event_id = CONCAT('legacy-', id)
WHERE event_id IS NULL;

ALTER TABLE t_article_behavior_event
    MODIFY COLUMN event_id VARCHAR(64) NOT NULL COMMENT '全局幂等事件ID';

SET @index_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_article_behavior_event'
      AND index_name = 'uk_behavior_event_id'
);
SET @sql := IF(
    @index_exists = 0,
    'ALTER TABLE t_article_behavior_event ADD UNIQUE KEY uk_behavior_event_id (event_id)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_article_behavior_event'
      AND index_name = 'idx_event_time_article'
);
SET @sql := IF(
    @index_exists = 0,
    'ALTER TABLE t_article_behavior_event ADD KEY idx_event_time_article (event_time, article_id)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_hot_rank_snapshot'
      AND index_name = 'uk_board_period_scope_rank'
);
SET @sql := IF(
    @index_exists = 0,
    'ALTER TABLE t_hot_rank_snapshot ADD UNIQUE KEY uk_board_period_scope_rank (board_type, period_key, category_scope, rank_no)',
    IF(
        (
            SELECT MAX(non_unique)
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 't_hot_rank_snapshot'
              AND index_name = 'uk_board_period_scope_rank'
        ) = 1,
        'ALTER TABLE t_hot_rank_snapshot DROP INDEX uk_board_period_scope_rank, ADD UNIQUE KEY uk_board_period_scope_rank (board_type, period_key, category_scope, rank_no)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_hot_rank_snapshot'
      AND index_name = 'uk_board_period_scope_article'
);
SET @sql := IF(
    @index_exists = 0,
    'ALTER TABLE t_hot_rank_snapshot ADD UNIQUE KEY uk_board_period_scope_article (board_type, period_key, category_scope, article_id)',
    IF(
        (
            SELECT MAX(non_unique)
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 't_hot_rank_snapshot'
              AND index_name = 'uk_board_period_scope_article'
        ) = 1,
        'ALTER TABLE t_hot_rank_snapshot DROP INDEX uk_board_period_scope_article, ADD UNIQUE KEY uk_board_period_scope_article (board_type, period_key, category_scope, article_id)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
