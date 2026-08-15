SET NAMES utf8mb4;

-- 已执行 search.sql 的独立增量迁移：补齐搜索历史表的存储引擎与字符集。
ALTER TABLE t_search_history
    ENGINE = InnoDB,
    DEFAULT CHARACTER SET utf8mb4,
    COLLATE = utf8mb4_unicode_ci;
