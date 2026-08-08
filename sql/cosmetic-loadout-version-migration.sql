USE game_community;
SET NAMES utf8mb4;

SET @has_loadout_version := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 't_user_cosmetic_loadout'
      AND column_name = 'version'
);
SET @add_loadout_version_sql := IF(
    @has_loadout_version = 0,
    'ALTER TABLE t_user_cosmetic_loadout ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号'' AFTER profile_bg_code',
    'SELECT 1'
);
PREPARE add_loadout_version FROM @add_loadout_version_sql;
EXECUTE add_loadout_version;
DEALLOCATE PREPARE add_loadout_version;
