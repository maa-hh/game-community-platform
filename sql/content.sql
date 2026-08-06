CREATE TABLE IF NOT EXISTS t_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '分类ID',
    name VARCHAR(64) NOT NULL COMMENT '分类名称',
    description VARCHAR(255) DEFAULT NULL COMMENT '分类描述',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    sort INT NOT NULL DEFAULT 0 COMMENT '排序值，越大越靠前',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY idx_category_name_deleted (name, deleted),
    KEY idx_category_status_sort (status, deleted, sort, id)
) COMMENT='内容分类表';

CREATE TABLE IF NOT EXISTS t_article (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文章ID',
    public_id VARCHAR(32) NOT NULL COMMENT '对外公开帖子ID',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    user_id BIGINT NOT NULL COMMENT '作者用户ID',
    title VARCHAR(80) NOT NULL COMMENT '标题',
    summary VARCHAR(200) DEFAULT NULL COMMENT '摘要',
    cover_url VARCHAR(1024) DEFAULT NULL COMMENT '封面图URL（待审可为 pending://objectKey）',
    post_type TINYINT NOT NULL DEFAULT 2 COMMENT '发帖模式: 1-图文, 2-文章, 3-视频, 4-转发',
    ref_article_id VARCHAR(32) DEFAULT NULL COMMENT '转发引用的原帖public_id',
    video_url VARCHAR(1024) DEFAULT NULL COMMENT '主视频URL（视频模式；待审可为 pending://objectKey）',
    category_id BIGINT NOT NULL COMMENT '主分类ID（多分类首项）',
    category_ids JSON DEFAULT NULL COMMENT '分类ID列表（JSON数组）',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-草稿, 1-已发布, 2-待审核, 3-已下架, 4-审核驳回',
    audit_message VARCHAR(255) DEFAULT NULL COMMENT '最近一次审核结果或下架原因',
    scheduled_publish_time DATETIME DEFAULT NULL COMMENT '计划发布时间',
    published_time DATETIME DEFAULT NULL COMMENT '实际发布时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_article_public_id (public_id),
    KEY idx_article_user_status_time (user_id, status, deleted, update_time, id),
    KEY idx_article_category_publish (category_id, status, deleted, published_time, id),
    KEY idx_article_status_publish (status, deleted, published_time, id),
    KEY idx_article_ref_article (ref_article_id)
) COMMENT='文章骨架表，正文存 MongoDB';

CREATE TABLE IF NOT EXISTS t_article_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '审核流水ID',
    article_id BIGINT NOT NULL COMMENT '文章ID',
    audit_stage TINYINT NOT NULL COMMENT '审核阶段: 1-DFA, 2-AI文本, 3-AI图片',
    status TINYINT NOT NULL COMMENT '审核状态: 1-通过, 3-驳回',
    suggestion VARCHAR(16) DEFAULT NULL COMMENT 'pass/block',
    reason VARCHAR(255) DEFAULT NULL COMMENT '审核原因',
    audit_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '审核完成时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_article_audit_article_time (article_id, audit_time, id)
) COMMENT='文章审核流水表';

CREATE TABLE IF NOT EXISTS t_article_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    article_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_article_category (article_id, category_id),
    KEY idx_category_article (category_id, article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章分类关系';

CREATE TABLE IF NOT EXISTS t_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '任务ID',
    type TINYINT NOT NULL COMMENT '任务类型: 1-文章审核发布',
    param JSON NOT NULL COMMENT '任务参数JSON',
    business_id BIGINT NOT NULL COMMENT '关联业务ID',
    execute_time DATETIME DEFAULT NULL COMMENT '执行时间，为空表示立即执行',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '任务状态: 0-待执行, 1-执行中, 2-已完成, 3-失败, 4-已取消',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    max_retry_count INT NOT NULL DEFAULT 3 COMMENT '最大重试次数',
    error_msg VARCHAR(255) DEFAULT NULL COMMENT '最后一次错误信息',
    queued TINYINT NOT NULL DEFAULT 0 COMMENT '是否已入 Redis 队列: 0-否, 1-是',
    lease_token VARCHAR(64) DEFAULT NULL COMMENT '执行租约令牌',
    lease_expire_time DATETIME DEFAULT NULL COMMENT '执行租约到期时间',
    active_task_key VARCHAR(96) GENERATED ALWAYS AS
        (CASE WHEN status IN (0, 1) THEN CONCAT(type, ':', business_id) ELSE NULL END) STORED,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    KEY idx_task_status_queue_execute (status, queued, execute_time, id),
    KEY idx_task_status_lease (status, lease_expire_time, update_time, id),
    KEY idx_task_business_type (business_id, type, status),
    UNIQUE KEY uk_task_active_business (active_task_key)
) COMMENT='内容任务表';

CREATE TABLE IF NOT EXISTS t_task_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '任务日志ID',
    task_id BIGINT NOT NULL COMMENT '任务ID',
    type TINYINT NOT NULL COMMENT '任务类型',
    business_id BIGINT NOT NULL COMMENT '关联业务ID',
    status TINYINT NOT NULL COMMENT '执行结果: 0-成功, 1-失败',
    result_msg VARCHAR(255) DEFAULT NULL COMMENT '执行结果摘要',
    cost_time BIGINT DEFAULT NULL COMMENT '耗时毫秒',
    exception_msg VARCHAR(255) DEFAULT NULL COMMENT '异常信息',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_task_log_task_time (task_id, create_time, id)
) COMMENT='任务执行日志表';

/*
MongoDB 集合: t_article_content
{
  articleId: NumberLong,   // 唯一索引
  content: String,
  imageUrls: [String],
  userId: NumberLong,
  createTime: ISODate,
  updateTime: ISODate
}
建议索引:
db.t_article_content.createIndex({ articleId: 1 }, { unique: true })
db.t_article_content.createIndex({ userId: 1, updateTime: -1 })
*/
