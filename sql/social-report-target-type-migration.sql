-- 举报目标类型注释增量迁移。
-- social.sql 已在既有环境执行过，不能回写原始脚本触发 checksum 冲突。
USE game_community;
SET NAMES utf8mb4;

SET @has_target_type := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 't_social_report'
      AND column_name = 'target_type'
);
SET @modify_target_type_sql := IF(
    @has_target_type = 1,
    'ALTER TABLE t_social_report MODIFY COLUMN target_type TINYINT NOT NULL COMMENT ''目标类型: 1-文章, 2-评论, 3-回复, 4-用户, 5-弹幕, 6-问题反馈''',
    'SELECT 1'
);
PREPARE modify_target_type FROM @modify_target_type_sql;
EXECUTE modify_target_type;
DEALLOCATE PREPARE modify_target_type;
