CREATE TABLE IF NOT EXISTS t_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
  account_id BIGINT DEFAULT NULL COMMENT '对外账号ID',
  username VARCHAR(32) NOT NULL COMMENT '昵称',
  avatar VARCHAR(512) DEFAULT NULL COMMENT '头像URL',
  signature VARCHAR(120) DEFAULT NULL COMMENT '个性签名',
  phone VARCHAR(20) NOT NULL COMMENT '手机号',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0正常 1禁用/注销',
  type TINYINT NOT NULL DEFAULT 0 COMMENT '0普通用户 1管理员',
  game_account VARCHAR(64) DEFAULT NULL COMMENT '游戏账号',
  audit_status TINYINT NOT NULL DEFAULT 0 COMMENT '0无需审核/审核完成 1审核中',
  last_login_time DATETIME DEFAULT NULL COMMENT '最后登录时间',
  version INT NOT NULL DEFAULT 0 COMMENT '用户资料乐观锁版本号',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  UNIQUE KEY uk_account_id (account_id),
  UNIQUE KEY uk_phone_deleted (phone, deleted),
  KEY idx_username (username),
  KEY idx_status_type (status, type),
  KEY idx_game_account (game_account)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

SET @account_id_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 't_user'
    AND column_name = 'account_id'
);
SET @account_id_sql = IF(
  @account_id_exists = 0,
  'ALTER TABLE t_user ADD COLUMN account_id BIGINT NULL COMMENT ''对外账号ID'' AFTER id',
  'SELECT 1'
);
PREPARE stmt FROM @account_id_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE t_user SET account_id = id + 9999 WHERE account_id IS NULL OR account_id < 10000;

SET @account_id_not_null = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 't_user'
    AND column_name = 'account_id'
    AND is_nullable = 'NO'
);
SET @account_id_nullable_sql = IF(
  @account_id_not_null > 0,
  'ALTER TABLE t_user MODIFY COLUMN account_id BIGINT NULL COMMENT ''对外账号ID''',
  'SELECT 1'
);
PREPARE stmt FROM @account_id_nullable_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @account_id_index_exists = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 't_user'
    AND index_name = 'uk_account_id'
);
SET @account_id_index_sql = IF(
  @account_id_index_exists = 0,
  'ALTER TABLE t_user ADD UNIQUE KEY uk_account_id (account_id)',
  'SELECT 1'
);
PREPARE stmt FROM @account_id_index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @last_login_time_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 't_user'
    AND column_name = 'last_login_time'
);
SET @last_login_time_sql = IF(
  @last_login_time_exists = 0,
  'ALTER TABLE t_user ADD COLUMN last_login_time DATETIME DEFAULT NULL COMMENT ''最后登录时间''',
  'SELECT 1'
);
PREPARE stmt FROM @last_login_time_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @user_version_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 't_user'
    AND column_name = 'version'
);
SET @user_version_sql = IF(
  @user_version_exists = 0,
  'ALTER TABLE t_user ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT ''用户资料乐观锁版本号'' AFTER last_login_time',
  'SELECT 1'
);
PREPARE stmt FROM @user_version_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS t_user_auth (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '认证记录ID',
  user_id BIGINT NOT NULL COMMENT '用户主键ID',
  password VARCHAR(64) NOT NULL COMMENT '加盐密码',
  salt VARCHAR(32) NOT NULL COMMENT '密码盐值',
  version INT NOT NULL DEFAULT 0 COMMENT '认证数据乐观锁版本号',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  UNIQUE KEY uk_user_auth_user_deleted (user_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户认证表';

SET @password_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 't_user'
    AND column_name = 'password'
);
SET @salt_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 't_user'
    AND column_name = 'salt'
);
SET @migrate_auth_sql = IF(
  @password_exists > 0 AND @salt_exists > 0,
  'INSERT INTO t_user_auth (user_id, password, salt, version, create_time, update_time, deleted)
   SELECT u.id, u.password, u.salt, 0, u.create_time, u.update_time, u.deleted
   FROM t_user u
   LEFT JOIN t_user_auth a ON a.user_id = u.id AND a.deleted = 0
   WHERE a.id IS NULL',
  'SELECT 1'
);
PREPARE stmt FROM @migrate_auth_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_password_sql = IF(
  @password_exists > 0,
  'ALTER TABLE t_user DROP COLUMN password',
  'SELECT 1'
);
PREPARE stmt FROM @drop_password_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_salt_sql = IF(
  @salt_exists > 0,
  'ALTER TABLE t_user DROP COLUMN salt',
  'SELECT 1'
);
PREPARE stmt FROM @drop_salt_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @auth_version_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 't_user_auth'
    AND column_name = 'version'
);
SET @auth_version_sql = IF(
  @auth_version_exists = 0,
  'ALTER TABLE t_user_auth ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT ''认证数据乐观锁版本号'' AFTER salt',
  'SELECT 1'
);
PREPARE stmt FROM @auth_version_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @audit_status_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 't_user'
    AND column_name = 'audit_status'
);
SET @audit_status_sql = IF(
  @audit_status_exists = 0,
  'ALTER TABLE t_user ADD COLUMN audit_status TINYINT NOT NULL DEFAULT 0 COMMENT ''0无需审核/审核完成 1审核中'' AFTER game_account',
  'SELECT 1'
);
PREPARE stmt FROM @audit_status_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS t_user_audit_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '审核任务ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  task_type VARCHAR(32) NOT NULL COMMENT '任务类型: PROFILE/AVATAR',
  status VARCHAR(32) NOT NULL COMMENT '任务状态: PENDING/PROCESSING/PASSED/REJECTED/FAILED',
  payload TEXT NOT NULL COMMENT '审核负载JSON',
  error_message VARCHAR(255) DEFAULT NULL COMMENT '失败原因',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  KEY idx_user_task_status (user_id, task_type, status),
  KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户审核任务表';
