USE game_community;
SET NAMES utf8mb4;

-- 短评表内部 id 只用于数据库关联；接口和前端使用不可枚举的 review_id。
SET @has_review_id := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 't_game_review'
      AND column_name = 'review_id'
);
SET @add_review_id_sql := IF(
    @has_review_id = 0,
    'ALTER TABLE t_game_review ADD COLUMN review_id VARCHAR(32) NULL COMMENT ''对外短评标识'' AFTER id',
    'SELECT 1'
);
PREPARE add_review_id FROM @add_review_id_sql;
EXECUTE add_review_id;
DEALLOCATE PREPARE add_review_id;

UPDATE t_game_review
SET review_id = REPLACE(UUID(), '-', '')
WHERE review_id IS NULL OR review_id = '';

SET @has_review_id_index := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_game_review'
      AND index_name = 'uk_game_review_public_id'
);
SET @add_review_id_index_sql := IF(
    @has_review_id_index = 0,
    'ALTER TABLE t_game_review ADD UNIQUE KEY uk_game_review_public_id (review_id)',
    'SELECT 1'
);
PREPARE add_review_id_index FROM @add_review_id_index_sql;
EXECUTE add_review_id_index;
DEALLOCATE PREPARE add_review_id_index;

ALTER TABLE t_game_review
    MODIFY review_id VARCHAR(32) NOT NULL COMMENT '对外短评标识';
