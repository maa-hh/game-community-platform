-- 审核线程池拒绝日志（已有库增量）
CREATE TABLE IF NOT EXISTS t_user_audit_reject_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '请求用户内部ID',
    account_id BIGINT DEFAULT NULL COMMENT '对外账号ID',
    task_id BIGINT DEFAULT NULL COMMENT '关联审核任务ID',
    task_type VARCHAR(32) NOT NULL COMMENT 'USERNAME/SIGNATURE/AVATAR',
    request_data TEXT NOT NULL COMMENT '请求附带数据JSON',
    reject_reason VARCHAR(255) NOT NULL COMMENT '拒绝原因',
    pool_snapshot VARCHAR(255) DEFAULT NULL COMMENT '线程池快照',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_reject_user_time (user_id, create_time),
    KEY idx_reject_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核线程池拒绝日志';
