-- 统一审核工单生产化增量迁移。
-- 兼容已经手工创建旧版 t_moderation_task 的环境。
USE game_community;

DROP PROCEDURE IF EXISTS sp_moderation_add_column;
DELIMITER $$
CREATE PROCEDURE sp_moderation_add_column(IN p_name VARCHAR(64), IN p_definition TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 't_moderation_task' AND column_name = p_name
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE t_moderation_task ADD COLUMN ', p_name, ' ', p_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL sp_moderation_add_column('claim_token', "VARCHAR(64) NOT NULL DEFAULT '' COMMENT '认领租约令牌' AFTER handle_time");
CALL sp_moderation_add_column('lease_expire_time', "DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '认领租约到期时间' AFTER claim_token");
CALL sp_moderation_add_column('action_request_id', "VARCHAR(64) NOT NULL DEFAULT '' COMMENT '当前处理请求幂等标识' AFTER lease_expire_time");
CALL sp_moderation_add_column('last_error', "VARCHAR(255) NOT NULL DEFAULT '' COMMENT '最近一次处理错误' AFTER action_request_id");
CALL sp_moderation_add_column('attempt_count', "INT NOT NULL DEFAULT 0 COMMENT '处理尝试次数' AFTER last_error");
DROP PROCEDURE sp_moderation_add_column;

ALTER TABLE t_moderation_task
    MODIFY source_id BIGINT NOT NULL;

SET @has_legacy_unique = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 't_moderation_task'
      AND index_name = 'uk_moderation_source'
);
SET @ddl = IF(@has_legacy_unique > 0,
              'ALTER TABLE t_moderation_task DROP INDEX uk_moderation_source',
              'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_active_key = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 't_moderation_task'
      AND column_name = 'active_source_key'
);
SET @ddl = IF(@has_active_key = 0,
              "ALTER TABLE t_moderation_task ADD COLUMN active_source_key VARCHAR(96) GENERATED ALWAYS AS (CASE WHEN status IN (0, 1) THEN CONCAT(task_type, ':', source_id) ELSE NULL END) STORED",
              'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_active_unique = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 't_moderation_task'
      AND index_name = 'uk_moderation_active_source'
);
SET @ddl = IF(@has_active_unique = 0,
              'ALTER TABLE t_moderation_task ADD UNIQUE KEY uk_moderation_active_source (active_source_key)',
              'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_status_type_index = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 't_moderation_task'
      AND index_name = 'idx_moderation_status_type_time'
);
SET @ddl = IF(@has_status_type_index = 0,
              'ALTER TABLE t_moderation_task ADD KEY idx_moderation_status_type_time (status, task_type, create_time, id)',
              'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_lease_index = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 't_moderation_task'
      AND index_name = 'idx_moderation_status_lease'
);
SET @ddl = IF(@has_lease_index = 0,
              'ALTER TABLE t_moderation_task ADD KEY idx_moderation_status_lease (status, lease_expire_time, id)',
              'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_type_source_index = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 't_moderation_task'
      AND index_name = 'idx_moderation_type_source'
);
SET @ddl = IF(@has_type_source_index = 0,
              'ALTER TABLE t_moderation_task ADD KEY idx_moderation_type_source (task_type, source_id, id)',
              'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
