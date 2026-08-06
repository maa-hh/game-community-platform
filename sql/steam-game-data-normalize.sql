-- Steam 游戏目录最终数据规范化：列表字段不使用 NULL
USE game_community;
SET NAMES utf8mb4;

UPDATE t_game_catalog
SET avg_score = COALESCE(avg_score, 0.0),
    steam_review_score = COALESCE(steam_review_score, 0),
    steam_review_count = COALESCE(steam_review_count, 0),
    steam_is_free = COALESCE(steam_is_free, 0),
    price_currency = COALESCE(NULLIF(price_currency, ''), 'CNY'),
    price_initial = COALESCE(price_initial, 0),
    price_final = COALESCE(price_final, 0),
    price_discount = COALESCE(price_discount, 0),
    price_formatted = COALESCE(NULLIF(price_formatted, ''),
        CASE WHEN steam_is_free = 1 THEN '免费' ELSE '暂无价格' END),
    static_synced_at = COALESCE(static_synced_at, steam_synced_at, create_time),
    metrics_synced_at = COALESCE(metrics_synced_at, steam_synced_at, create_time),
    price_synced_at = COALESCE(price_synced_at, steam_synced_at, create_time),
    rich_synced_at = COALESCE(rich_synced_at, steam_synced_at, create_time),
    last_refresh_attempt_at = COALESCE(last_refresh_attempt_at, steam_synced_at, create_time),
    next_refresh_at = COALESCE(next_refresh_at, DATE_ADD(COALESCE(steam_synced_at, create_time), INTERVAL 7 DAY)),
    refresh_status = COALESCE(NULLIF(refresh_status, ''), 'READY'),
    detail_ready = COALESCE(detail_ready, 1);

ALTER TABLE t_game_catalog
    MODIFY avg_score DECIMAL(4,1) NOT NULL DEFAULT 0.0,
    MODIFY steam_review_score INT NOT NULL DEFAULT 0 COMMENT 'Steam 好评率 0-100',
    MODIFY steam_review_count INT NOT NULL DEFAULT 0 COMMENT 'Steam 评价总数',
    MODIFY steam_is_free TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否免费',
    MODIFY price_currency VARCHAR(8) NOT NULL DEFAULT 'CNY' COMMENT '价格币种',
    MODIFY price_initial INT NOT NULL DEFAULT 0 COMMENT '原价，单位分',
    MODIFY price_final INT NOT NULL DEFAULT 0 COMMENT '现价，单位分',
    MODIFY price_discount INT NOT NULL DEFAULT 0 COMMENT '折扣百分比',
    MODIFY price_formatted VARCHAR(32) NOT NULL DEFAULT '暂无价格' COMMENT '展示价格',
    MODIFY static_synced_at DATETIME NOT NULL COMMENT '静态数据最近成功更新时间',
    MODIFY metrics_synced_at DATETIME NOT NULL COMMENT 'Steam 评分数据最近成功更新时间',
    MODIFY price_synced_at DATETIME NOT NULL COMMENT '价格数据最近成功更新时间',
    MODIFY rich_synced_at DATETIME NOT NULL COMMENT 'Mongo 富详情最近成功更新时间',
    MODIFY last_refresh_attempt_at DATETIME NOT NULL COMMENT '最近一次刷新尝试时间',
    MODIFY next_refresh_at DATETIME NOT NULL COMMENT '下一次允许刷新时间';
