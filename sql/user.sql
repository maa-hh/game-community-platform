-- ============================================================
-- 用户模块（邮箱注册登录，无手机号字段）
-- 仅负责首次建表；已存在的表和用户数据不得被删除或重建。
-- ============================================================

CREATE TABLE IF NOT EXISTS t_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    account_id BIGINT NOT NULL COMMENT '对外账号ID',
    username VARCHAR(20) NOT NULL COMMENT '昵称',
    email VARCHAR(128) NOT NULL COMMENT '邮箱（登录凭证）',
    avatar VARCHAR(512) NOT NULL DEFAULT '' COMMENT '头像URL',
    signature VARCHAR(50) NOT NULL DEFAULT '' COMMENT '个性签名',
    steam_account VARCHAR(64) DEFAULT '' COMMENT 'Steam账号',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_account_id (account_id),
    UNIQUE KEY uk_email_deleted (email, deleted),
    KEY idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户资料表';

CREATE TABLE IF NOT EXISTS t_user_profile_audit (
    user_id BIGINT PRIMARY KEY COMMENT '用户ID',
    username_audit_status TINYINT NOT NULL DEFAULT 0 COMMENT '0空闲 1审核中 2人工复核',
    signature_audit_status TINYINT NOT NULL DEFAULT 0 COMMENT '0空闲 1审核中 2人工复核',
    avatar_audit_status TINYINT NOT NULL DEFAULT 0 COMMENT '0空闲 1审核中 2人工复核',
    pending_username VARCHAR(20) NOT NULL DEFAULT '' COMMENT '待审昵称',
    pending_signature VARCHAR(50) NOT NULL DEFAULT '' COMMENT '待审签名',
    pending_avatar VARCHAR(512) NOT NULL DEFAULT '' COMMENT '待审头像私有对象名',
    version INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户资料字段审核状态';

CREATE TABLE IF NOT EXISTS t_user_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '账户ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0正常 1封禁 2注销中 3已注销',
    type TINYINT NOT NULL DEFAULT 0 COMMENT '0普通 1管理员',
    ban_until DATETIME DEFAULT NULL COMMENT '封禁截止',
    ban_reason VARCHAR(255) NOT NULL DEFAULT '' COMMENT '封禁原因',
    cancel_at DATETIME DEFAULT NULL COMMENT '计划注销时间',
    register_source VARCHAR(32) NOT NULL DEFAULT 'EMAIL' COMMENT '注册来源',
    last_login_time DATETIME DEFAULT NULL,
    last_login_ip VARCHAR(45) NOT NULL DEFAULT '',
    version INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_user_account_user_deleted (user_id, deleted),
    KEY idx_status_ban_until (status, ban_until),
    KEY idx_cancel_at_status (cancel_at, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户账户表';

CREATE TABLE IF NOT EXISTS t_user_auth (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '认证ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    password VARCHAR(128) NOT NULL COMMENT 'BCrypt密码',
    salt VARCHAR(32) NOT NULL DEFAULT '' COMMENT '盐（BCrypt留空）',
    fail_count INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    lock_until DATETIME DEFAULT NULL COMMENT '锁定截止',
    last_password_change DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_user_auth_user_deleted (user_id, deleted),
    KEY idx_lock_until (lock_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户认证表';

CREATE TABLE IF NOT EXISTS t_account_id_pool (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    account_id BIGINT NOT NULL COMMENT '可用账号ID',
    digit_count TINYINT NOT NULL COMMENT '位数',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0可用 1已占用',
    user_id BIGINT DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_account_id (account_id),
    KEY idx_status_digit (status, digit_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账号ID号池';

CREATE TABLE IF NOT EXISTS t_user_audit_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    task_type VARCHAR(32) NOT NULL COMMENT 'USERNAME/SIGNATURE/AVATAR',
    status VARCHAR(32) NOT NULL COMMENT 'PENDING/PROCESSING/PASSED/REJECTED/HUMAN_REVIEW/FAILED',
    pending_content VARCHAR(512) NOT NULL DEFAULT '' COMMENT '待审文本或头像对象名',
    payload TEXT NOT NULL COMMENT 'JSON 扩展负载',
    audit_mode VARCHAR(16) NOT NULL DEFAULT 'MOCK' COMMENT 'MOCK/LLM',
    score INT DEFAULT NULL COMMENT '合规分 0-10',
    error_message VARCHAR(255) NOT NULL DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_user_task_status (user_id, task_type, status),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户字段审核任务';

CREATE TABLE IF NOT EXISTS t_user_audit_reject_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '请求用户内部ID',
    account_id BIGINT DEFAULT NULL COMMENT '对外账号ID',
    task_id BIGINT DEFAULT NULL COMMENT '关联审核任务ID',
    task_type VARCHAR(32) NOT NULL COMMENT 'USERNAME/SIGNATURE/AVATAR',
    request_data TEXT NOT NULL COMMENT '请求附带数据JSON',
    reject_reason VARCHAR(255) NOT NULL COMMENT '拒绝原因',
    pool_snapshot VARCHAR(255) NOT NULL DEFAULT '' COMMENT '线程池快照',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_reject_user_time (user_id, create_time),
    KEY idx_reject_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核线程池拒绝日志';

CREATE TABLE IF NOT EXISTS t_user_operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    operator_id BIGINT DEFAULT NULL,
    operation VARCHAR(64) NOT NULL,
    detail TEXT NOT NULL DEFAULT ('') COMMENT '操作详情',
    ip VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_user_op_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户操作日志';

CREATE TABLE IF NOT EXISTS t_user_notification_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_key VARCHAR(96) NOT NULL,
    payload JSON NOT NULL,
    status TINYINT NOT NULL DEFAULT 0,
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_time DATETIME DEFAULT NULL,
    lock_token VARCHAR(64) DEFAULT NULL,
    lock_time DATETIME DEFAULT NULL,
    last_error VARCHAR(1000) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_notification_outbox_event (event_key),
    KEY idx_user_notification_outbox_pending (status, next_retry_time, id),
    KEY idx_user_notification_outbox_lock (status, lock_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户通知可靠事件 Outbox';
