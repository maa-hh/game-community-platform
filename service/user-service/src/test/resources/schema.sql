DROP TABLE IF EXISTS t_user;
DROP TABLE IF EXISTS t_user_audit_task;

CREATE TABLE t_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  account_id BIGINT,
  username VARCHAR(32) NOT NULL,
  avatar VARCHAR(512),
  signature VARCHAR(120),
  phone VARCHAR(20) NOT NULL,
  status TINYINT NOT NULL DEFAULT 0,
  type TINYINT NOT NULL DEFAULT 0,
  game_account VARCHAR(64),
  audit_status TINYINT NOT NULL DEFAULT 0,
  last_login_time DATETIME,
  version INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  deleted TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE t_user_auth (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  password VARCHAR(64) NOT NULL,
  salt VARCHAR(32) NOT NULL,
  version INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  deleted TINYINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_account_id ON t_user(account_id);
CREATE UNIQUE INDEX uk_phone_deleted ON t_user(phone, deleted);
CREATE INDEX idx_username ON t_user(username);
CREATE UNIQUE INDEX uk_user_auth_user_deleted ON t_user_auth(user_id, deleted);

CREATE TABLE t_user_audit_task (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  task_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  payload CLOB NOT NULL,
  error_message VARCHAR(255),
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  deleted TINYINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_user_task_status ON t_user_audit_task(user_id, task_type, status);
