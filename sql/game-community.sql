-- 游戏社区：Steam 绑定、游戏库、游戏百科、评分、帖子关联
USE game_community;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS t_user_steam_bind (
    user_id            BIGINT PRIMARY KEY COMMENT '社区用户 ID',
    steam_id           VARCHAR(32) NOT NULL COMMENT 'SteamID64',
    persona_name       VARCHAR(100) NULL,
    avatar_url         VARCHAR(500) NULL,
    profile_url        VARCHAR(500) NULL,
    steam_level        INT NULL,
    game_count         INT NOT NULL DEFAULT 0,
    library_public     TINYINT NOT NULL DEFAULT 0 COMMENT '游戏库是否可同步',
    library_synced_at  DATETIME NULL,
    create_time        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_steam_id (steam_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户 Steam 绑定';

CREATE TABLE IF NOT EXISTS t_user_steam_game (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id            BIGINT NOT NULL,
    app_id             BIGINT NOT NULL COMMENT 'Steam appId',
    name               VARCHAR(200) NULL,
    icon_url           VARCHAR(500) NULL,
    playtime_forever   INT NOT NULL DEFAULT 0 COMMENT '分钟',
    playtime_two_weeks INT NOT NULL DEFAULT 0 COMMENT '近两周游玩分钟',
    last_played_at     DATETIME NULL COMMENT '最后游玩时间',
    achievement_unlocked INT NULL COMMENT '已解锁成就数',
    achievement_total  INT NULL COMMENT '成就总数',
    synced_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_app (user_id, app_id),
    KEY idx_user_sync (user_id, synced_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户 Steam 游戏库缓存';

CREATE TABLE IF NOT EXISTS t_game_catalog (
    app_id             BIGINT PRIMARY KEY COMMENT 'Steam appId 或内部 ID',
    steam_name         VARCHAR(200) NULL,
    display_name       VARCHAR(200) NULL,
    header_image       VARCHAR(500) NULL,
    developers         JSON NULL,
    publishers         JSON NULL,
    genres             JSON NULL,
    release_date       VARCHAR(32) NULL,
    steam_url          VARCHAR(500) NULL,
    community_short    VARCHAR(1000) NULL,
    community_about    MEDIUMTEXT NULL,
    cover_override     VARCHAR(500) NULL,
    desc_source        VARCHAR(32) NOT NULL DEFAULT 'COMMUNITY_FIRST',
    discuss_count      INT NOT NULL DEFAULT 0,
    review_count       INT NOT NULL DEFAULT 0,
    avg_score          DECIMAL(4,1) NOT NULL DEFAULT 0.0,
    steam_review_score INT NOT NULL DEFAULT 0 COMMENT 'Steam 好评率 0-100',
    steam_review_count INT NOT NULL DEFAULT 0 COMMENT 'Steam 评价总数',
    steam_is_free      TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否免费',
    price_currency     VARCHAR(8) NOT NULL DEFAULT 'CNY' COMMENT '价格币种',
    price_initial      INT NOT NULL DEFAULT 0 COMMENT '原价，单位分',
    price_final        INT NOT NULL DEFAULT 0 COMMENT '现价，单位分',
    price_discount     INT NOT NULL DEFAULT 0 COMMENT '折扣百分比',
    price_discount_end_at BIGINT NULL COMMENT '促销截止 Unix 秒',
    price_formatted    VARCHAR(32) NOT NULL DEFAULT '暂无价格' COMMENT '展示价格',
    steam_synced_at    DATETIME NULL,
    status             TINYINT NOT NULL DEFAULT 1,
    create_time        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='游戏百科';

CREATE TABLE IF NOT EXISTS t_game_review (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    app_id             BIGINT NOT NULL,
    user_id            BIGINT NOT NULL,
    score              TINYINT NOT NULL COMMENT '1-10',
    content            VARCHAR(2000) NULL,
    status             TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0删除',
    create_time        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_game_user (app_id, user_id),
    KEY idx_game_time (app_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='游戏评分短评';

CREATE TABLE IF NOT EXISTS t_article_game (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    article_id         BIGINT NOT NULL,
    game_app_id        BIGINT NOT NULL,
    create_time        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_article_game (article_id, game_app_id),
    KEY idx_game_feed (game_app_id, article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子关联游戏标签';
