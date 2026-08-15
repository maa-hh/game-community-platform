-- 审核工单对外标识迁移：外部接口只使用 public_id，保留自增 id 作为内部关联键。
USE game_community;

SET @has_public_id = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 't_moderation_task'
      AND column_name = 'public_id'
);
SET @ddl = IF(@has_public_id = 0,
              "ALTER TABLE t_moderation_task ADD COLUMN public_id VARCHAR(64) NULL COMMENT '对外工单标识，禁止使用数据库主键' AFTER id",
              'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE t_moderation_task
SET public_id = UUID()
WHERE public_id IS NULL OR public_id = '';

ALTER TABLE t_moderation_task
    MODIFY public_id VARCHAR(64) NOT NULL COMMENT '对外工单标识，禁止使用数据库主键';

SET @has_public_id_unique = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 't_moderation_task'
      AND index_name = 'uk_moderation_public_id'
);
SET @ddl = IF(@has_public_id_unique = 0,
              'ALTER TABLE t_moderation_task ADD UNIQUE KEY uk_moderation_public_id (public_id)',
              'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
