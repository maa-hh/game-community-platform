-- 装扮查询性能升级：按用户读取有效效果，应用层按开始时间倒序整理。
USE game_community;
SET NAMES utf8mb4;

SET @old_index_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_user_active_effect'
      AND index_name = 'idx_active_effect_user'
);
SET @new_index_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_user_active_effect'
      AND index_name = 'idx_active_effect_user_start'
);
SET @index_ddl := CASE
    WHEN @new_index_exists > 0 THEN 'SELECT 1'
    WHEN @old_index_exists > 0 THEN
        'ALTER TABLE t_user_active_effect DROP INDEX idx_active_effect_user, ADD KEY idx_active_effect_user_start (user_id, start_at, expire_at)'
    ELSE
        'ALTER TABLE t_user_active_effect ADD KEY idx_active_effect_user_start (user_id, start_at, expire_at)'
END;
PREPARE cosmetic_index_stmt FROM @index_ddl;
EXECUTE cosmetic_index_stmt;
DEALLOCATE PREPARE cosmetic_index_stmt;
