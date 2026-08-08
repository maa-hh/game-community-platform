-- 商城 v3 后续索引修正：已执行旧版 v3 迁移的数据库单独收敛到当前索引。
SET @has_old_shop_item_status_index := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_shop_item'
      AND index_name = 'idx_shop_item_status'
);
SET @drop_old_shop_item_status_index_sql := IF(
    @has_old_shop_item_status_index > 0,
    'ALTER TABLE t_shop_item DROP INDEX idx_shop_item_status',
    'SELECT 1'
);
PREPARE drop_old_shop_item_status_index FROM @drop_old_shop_item_status_index_sql;
EXECUTE drop_old_shop_item_status_index;
DEALLOCATE PREPARE drop_old_shop_item_status_index;

SET @has_shop_item_status_time_index := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_shop_item'
      AND index_name = 'idx_shop_item_status_time'
);
SET @add_shop_item_status_time_index_sql := IF(
    @has_shop_item_status_time_index = 0,
    'ALTER TABLE t_shop_item ADD KEY idx_shop_item_status_time (status, create_time, id)',
    'SELECT 1'
);
PREPARE add_shop_item_status_time_index FROM @add_shop_item_status_time_index_sql;
EXECUTE add_shop_item_status_time_index;
DEALLOCATE PREPARE add_shop_item_status_time_index;
