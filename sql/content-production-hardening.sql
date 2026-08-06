-- 内容服务生产级并发、任务和可靠事件 Outbox 增量结构。
-- 脚本由 scripts/db/sync-mysql.sh 按 migrations.order 执行。
USE game_community;
SET NAMES utf8mb4;

SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 't_article' AND column_name = 'version'
);
SET @sql := IF(@column_exists = 0,
    "ALTER TABLE t_article ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本'",
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 't_task' AND column_name = 'lease_token'
);
SET @sql := IF(@column_exists = 0,
    "ALTER TABLE t_task ADD COLUMN lease_token VARCHAR(64) DEFAULT NULL COMMENT '执行租约令牌'",
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 't_task' AND column_name = 'lease_expire_time'
);
SET @sql := IF(@column_exists = 0,
    "ALTER TABLE t_task ADD COLUMN lease_expire_time DATETIME DEFAULT NULL COMMENT '执行租约到期时间'",
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 't_task' AND column_name = 'active_task_key'
);
SET @sql := IF(@column_exists = 0,
    "ALTER TABLE t_task ADD COLUMN active_task_key VARCHAR(96) GENERATED ALWAYS AS (CASE WHEN status IN (0, 1) THEN CONCAT(type, ':', business_id) ELSE NULL END) STORED",
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS t_content_outbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_key VARCHAR(160) NOT NULL COMMENT '业务幂等键',
    event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
    topic VARCHAR(128) DEFAULT NULL COMMENT 'Kafka topic，远程调用事件为空',
    message_key VARCHAR(128) DEFAULT NULL,
    payload JSON NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待发送 1发送中 2已发送 3重试 4死信',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_time DATETIME DEFAULT NULL,
    lock_token VARCHAR(64) DEFAULT NULL,
    lock_time DATETIME DEFAULT NULL,
    last_error VARCHAR(1000) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_content_outbox_event_key (event_key),
    KEY idx_content_outbox_pending (status, next_retry_time, id),
    KEY idx_content_outbox_lock (status, lock_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容可靠事件 Outbox';

CREATE TABLE IF NOT EXISTS t_article_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    article_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_article_category (article_id, category_id),
    KEY idx_category_article (category_id, article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章分类关系';

INSERT IGNORE INTO t_article_category (article_id, category_id)
SELECT a.id, jt.category_id
FROM t_article a
JOIN JSON_TABLE(a.category_ids, '$[*]' COLUMNS (category_id BIGINT PATH '$')) jt
WHERE a.category_ids IS NOT NULL;

-- 任务恢复、取消和按业务查询索引。
SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 't_task' AND index_name = 'idx_task_status_lease'
);
SET @sql := IF(@idx_exists = 0,
    'ALTER TABLE t_task ADD KEY idx_task_status_lease (status, lease_expire_time, update_time, id)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 't_category' AND index_name = 'idx_category_status_deleted_sort'
);
SET @sql := IF(@idx_exists = 0,
    'ALTER TABLE t_category ADD KEY idx_category_status_deleted_sort (status, deleted, sort, id)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 't_task' AND index_name = 'uk_task_active_business'
);
SET @sql := IF(@idx_exists = 0,
    'ALTER TABLE t_task ADD UNIQUE KEY uk_task_active_business (active_task_key)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 't_article' AND index_name = 'idx_article_feed'
);
SET @sql := IF(@idx_exists = 0,
    'ALTER TABLE t_article ADD KEY idx_article_feed (status, deleted, published_time, id)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
