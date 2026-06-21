CREATE TABLE IF NOT EXISTS t_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '分类ID',
    name VARCHAR(64) NOT NULL COMMENT '分类名称',
    description VARCHAR(255) DEFAULT NULL COMMENT '分类描述',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    sort INT NOT NULL DEFAULT 0 COMMENT '排序值，越大越靠前',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除'
) COMMENT='内容分类表';

CREATE UNIQUE INDEX idx_category_name_deleted ON t_category(name, deleted);
CREATE INDEX idx_category_status_sort ON t_category(status, sort, id);

CREATE TABLE IF NOT EXISTS t_article (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文章ID',
    user_id BIGINT NOT NULL COMMENT '作者用户ID',
    title VARCHAR(80) NOT NULL COMMENT '标题',
    summary VARCHAR(200) DEFAULT NULL COMMENT '摘要',
    cover_url VARCHAR(1024) DEFAULT NULL COMMENT '封面图URL',
    category_id BIGINT NOT NULL COMMENT '分类ID',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-草稿, 1-已发布, 2-待审核, 3-已下架, 4-审核驳回',
    audit_message VARCHAR(255) DEFAULT NULL COMMENT '最近一次审核结果或下架原因',
    scheduled_publish_time DATETIME DEFAULT NULL COMMENT '计划发布时间',
    published_time DATETIME DEFAULT NULL COMMENT '实际发布时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除'
) COMMENT='文章骨架表，正文存 MongoDB';

CREATE INDEX idx_article_user_status_time ON t_article(user_id, status, update_time, id);
CREATE INDEX idx_article_category_publish ON t_article(category_id, status, published_time, id);
CREATE INDEX idx_article_status_publish ON t_article(status, published_time, id);

CREATE TABLE IF NOT EXISTS t_article_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '审核流水ID',
    article_id BIGINT NOT NULL COMMENT '文章ID',
    audit_stage TINYINT NOT NULL COMMENT '审核阶段: 1-DFA, 2-AI文本, 3-AI图片',
    status TINYINT NOT NULL COMMENT '审核状态: 1-通过, 3-驳回',
    suggestion VARCHAR(16) DEFAULT NULL COMMENT 'pass/block',
    reason VARCHAR(255) DEFAULT NULL COMMENT '审核原因',
    audit_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '审核完成时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT='文章审核流水表';

CREATE INDEX idx_article_audit_article_time ON t_article_audit(article_id, audit_time, id);

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
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除'
) COMMENT='内容任务表';

CREATE INDEX idx_task_status_queue_execute ON t_task(status, queued, execute_time, id);
CREATE INDEX idx_task_business_type ON t_task(business_id, type, status);

CREATE TABLE IF NOT EXISTS t_task_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '任务日志ID',
    task_id BIGINT NOT NULL COMMENT '任务ID',
    type TINYINT NOT NULL COMMENT '任务类型',
    business_id BIGINT NOT NULL COMMENT '关联业务ID',
    status TINYINT NOT NULL COMMENT '执行结果: 0-成功, 1-失败',
    result_msg VARCHAR(255) DEFAULT NULL COMMENT '执行结果摘要',
    cost_time BIGINT DEFAULT NULL COMMENT '耗时毫秒',
    exception_msg VARCHAR(255) DEFAULT NULL COMMENT '异常信息',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT='任务执行日志表';

CREATE INDEX idx_task_log_task_time ON t_task_log(task_id, create_time, id);

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
