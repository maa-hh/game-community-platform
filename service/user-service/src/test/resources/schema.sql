DROP TABLE IF EXISTS t_user_operation_log;
DROP TABLE IF EXISTS t_user_audit_reject_log;
DROP TABLE IF EXISTS t_user_audit_task;
DROP TABLE IF EXISTS t_account_id_pool;
DROP TABLE IF EXISTS t_user_auth;
DROP TABLE IF EXISTS t_user_profile_audit;
DROP TABLE IF EXISTS t_user_account;
DROP TABLE IF EXISTS t_user;

CREATE TABLE t_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  account_id BIGINT NOT NULL,
  username VARCHAR(20) NOT NULL,
  email VARCHAR(128) NOT NULL,
  avatar VARCHAR(512) NOT NULL DEFAULT '',
  signature VARCHAR(100) NOT NULL DEFAULT '',
  steam_account VARCHAR(64) NOT NULL DEFAULT '',
  version INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  deleted TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE t_user_profile_audit (
  user_id BIGINT PRIMARY KEY,
  username_audit_status INT NOT NULL DEFAULT 0,
  signature_audit_status INT NOT NULL DEFAULT 0,
  avatar_audit_status INT NOT NULL DEFAULT 0,
  pending_username VARCHAR(20) NOT NULL DEFAULT '',
  pending_signature VARCHAR(100) NOT NULL DEFAULT '',
  pending_avatar VARCHAR(512) NOT NULL DEFAULT '',
  version INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL
);

CREATE TABLE t_user_account (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  status INT NOT NULL DEFAULT 0,
  type INT NOT NULL DEFAULT 0,
  ban_until DATETIME,
  ban_reason VARCHAR(255) NOT NULL DEFAULT '',
  cancel_at DATETIME,
  register_source VARCHAR(32) NOT NULL DEFAULT 'EMAIL',
  last_login_time DATETIME,
  last_login_ip VARCHAR(64) NOT NULL DEFAULT '',
  version INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  deleted TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE t_user_auth (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  password VARCHAR(100) NOT NULL,
  salt VARCHAR(64),
  fail_count INT NOT NULL DEFAULT 0,
  lock_until DATETIME,
  last_password_change DATETIME NOT NULL,
  version INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  deleted TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE t_account_id_pool (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  account_id BIGINT NOT NULL,
  digit_count INT NOT NULL,
  status TINYINT NOT NULL DEFAULT 0,
  user_id BIGINT,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL
);

CREATE TABLE t_user_audit_task (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  task_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  pending_content VARCHAR(512),
  payload CLOB NOT NULL,
  audit_mode VARCHAR(16),
  score INT,
  error_message VARCHAR(255),
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  deleted TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE t_user_audit_reject_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  account_id BIGINT,
  task_id BIGINT,
  task_type VARCHAR(32) NOT NULL,
  request_data CLOB NOT NULL,
  reject_reason VARCHAR(255) NOT NULL,
  pool_snapshot VARCHAR(255),
  create_time DATETIME NOT NULL
);

CREATE TABLE t_user_operation_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  operator_id BIGINT,
  operation VARCHAR(32) NOT NULL,
  detail VARCHAR(512),
  ip VARCHAR(64),
  create_time DATETIME NOT NULL
);

CREATE UNIQUE INDEX uk_account_id ON t_user(account_id);
CREATE UNIQUE INDEX uk_email_deleted ON t_user(email, deleted);
CREATE INDEX idx_username ON t_user(username);
CREATE UNIQUE INDEX uk_user_account_user_deleted ON t_user_account(user_id, deleted);
CREATE UNIQUE INDEX uk_user_auth_user_deleted ON t_user_auth(user_id, deleted);
CREATE UNIQUE INDEX uk_account_id_pool_account_id ON t_account_id_pool(account_id);
CREATE INDEX idx_account_id_pool_status_digit ON t_account_id_pool(status, digit_count);
CREATE INDEX idx_user_task_status ON t_user_audit_task(user_id, task_type, status);

INSERT INTO t_account_id_pool (account_id, digit_count, status, create_time, update_time) VALUES
(10000, 5, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(10001, 5, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(10002, 5, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(10003, 5, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(10004, 5, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(10005, 5, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(10006, 5, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(10007, 5, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(10008, 5, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(10009, 5, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(10010, 5, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
