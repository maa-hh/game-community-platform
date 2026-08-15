# Content Service Backend PRD

## 1. 模块目标

`content-service` 负责游戏社区的内容域基础能力，覆盖：

- 分类管理
- 图文文章创建、草稿、审核、发布、下架、删除
- 正文内容拆分到 MongoDB
- 图片上传到 MinIO 公共桶
- 审核与定时发布通过任务系统异步执行

这次重构基于 `demo` 的 `content-service` 迁入，但按当前项目规范做了收敛：

- 去掉了旧版对 `Kafka / XXL-Job / ES / 后台待审核表 / 粉丝 Feed` 的强耦合
- 审核能力统一改为 `DfaAuditUtils + AuditClient(MiniMax)` 链路
- 任务系统改为“事务提交后入 Redis + 数据库补偿回灌”
- 公共模型统一放到 `model` / `common` / `utils`，不在 `content-service` 内部造重复结构

## 2. 业务边界

### 2.1 当前落地能力

- 用户发文章草稿
- 用户提交文章发布
- 用户定时发布文章
- 用户查看文章详情
- 用户查看自己的文章列表
- 用户分页浏览内容广场
- 用户上传内容图片
- 管理员管理分类
- 管理员分页查看文章并进行状态操作

### 2.2 暂不在本轮直接接入的扩展点

- 搜索索引同步
- 消息通知中心
- 粉丝 Feed 推送
- 独立人工复审后台流

这些能力保留扩展位置，但不再用空依赖硬连进当前模块。

## 3. 数据模型

### 3.1 MySQL 表

#### `t_category`

作用：
- 内容分类主数据

关键字段：
- `id`
- `name`
- `description`
- `status`
- `sort`
- `deleted`

索引：
- `idx_category_name_deleted`
- `idx_category_status_sort`

#### `t_article`

作用：
- 存文章骨架和发布状态，不存长正文

关键字段：
- `id`
- `user_id`
- `title`
- `summary`
- `cover_url`
- `category_id`
- `status`
- `audit_message`
- `scheduled_publish_time`
- `published_time`

状态定义：
- `0` 草稿
- `1` 已发布
- `2` 待审核
- `3` 已下架
- `4` 审核驳回

索引：
- `idx_article_user_status_time`
- `idx_article_category_publish`
- `idx_article_status_publish`

#### `t_article_audit`

作用：
- 保存每次审核流水

关键字段：
- `article_id`
- `audit_stage`
- `status`
- `suggestion`
- `reason`
- `audit_time`

审核阶段：
- `1` DFA 本地文本
- `2` AI 文本审核
- `3` AI 图片审核

#### `t_task`

作用：
- 保存异步审核发布任务

关键字段：
- `type`
- `param`
- `business_id`
- `execute_time`
- `status`
- `retry_count`
- `max_retry_count`
- `queued`

设计要点：
- 任务先落 MySQL
- 提交后再放 Redis
- Redis 丢失可通过数据库补偿恢复

#### `t_task_log`

作用：
- 保存任务执行日志

### 3.2 MongoDB 集合

#### `t_article_content`

作用：
- 存文章正文与正文图片 URL

字段：
- `articleId`
- `content`
- `imageUrls`
- `userId`
- `createTime`
- `updateTime`

索引：
- `articleId unique`
- `userId + updateTime`

## 4. 核心流程

### 4.1 草稿保存

1. 前端提交 `POST /article`，`status=0`
2. 后端保存 `t_article`
3. 后端直接写 `t_article_content`
4. 返回文章 ID

特点：
- 不走异步任务
- 不走审核
- 适合编辑器自动保存和继续编辑

### 4.2 立即发布

1. 前端提交 `POST /article`，`status=1`
2. 后端保存 `t_article.status=2(待审核)`
3. 写入 `t_task`
4. 事务提交后再把任务 ID 推入 Redis 列表
5. 定时器扫描 Redis，并交给 `taskExecutor` 并发执行
6. `TaskServiceImpl` 在任务线程内同步调用 `ArticleAuditService`，不再二次 `@Async`
7. 审核通过：
   - 写 Mongo 正文
   - 更新 `t_article.status=1`
   - 写 `published_time`
8. 审核驳回：
   - 更新 `t_article.status=4`
   - 写 `audit_message`
   - 删除上传的公共桶图片

### 4.3 定时发布

1. 前端传 `scheduledPublishTime`
2. 任务入 `t_task`
3. 如果执行时间在未来 5 分钟内，提交后直接进入 Redis ZSet
4. 到时转移到立即执行队列
5. 后续流程与立即发布一致

### 4.4 任务补偿

问题场景：
- 事务未提交时 Redis 先入队
- 服务重启后 Redis 队列丢失
- 任务入 Redis 失败

当前方案：
- `TaskServiceImpl` 使用 `afterCommit` 提交后入队
- `ContentTaskRecoveryRunner` 启动时回灌 `queued=0` 的待执行任务
- `ContentTaskScheduler` 每 30 秒从数据库补偿回 Redis
- 异步边界统一收敛在 `TaskServiceImpl.executeTasks()`，审核发布业务方法本身保持同步事务执行，避免重复异步带来的事务和异常传播错位

这是本轮重构里最重要的执行链优化点。

## 5. 审核设计

### 5.1 审核链路

1. 本地 DFA 敏感词
2. MiniMax 文本审核
3. MiniMax 图片 URL 审核
4. 审核业务执行在任务线程内同步完成，任务成功或失败状态由 `TaskServiceImpl` 在同一条执行链上统一落库

### 5.2 审核结果处理

- 全部通过：发布
- 任一环节驳回：状态置为 `REJECTED`
- 审核原因写入 `audit_message`
- 每个审核环节都写一条 `t_article_audit`

### 5.3 为什么不用 demo 的 OCR + 阿里云旧链路

原因：
- 当前项目已统一了审核工具层
- 旧链路外部依赖多、耦合重
- OCR 与多供应商审核不是本轮落地的必要条件

后续如果真要补 OCR，建议放到 `utils.audit` 统一能力层，不再在业务服务里散落实现。

## 6. 接口清单

### 6.1 文章接口

#### `POST /article`

作用：
- 新建文章或保存草稿

请求参数：
- `id` 可选，更新时传
- `title`
- `summary`
- `content`
- `coverUrl`
- `imageUrls`
- `categoryId`
- `status`
- `scheduledPublishTime`

返回：
- `Long articleId`

#### `PUT /article/{id}`

作用：
- 更新文章并重新按草稿/发布逻辑处理

请求参数：
- 同 `POST /article`

#### `DELETE /article/{id}`

作用：
- 删除当前用户自己的文章

#### `GET /article/{id}`

作用：
- 获取文章详情

返回：
- `ArticleDetailVO`

#### `GET /article/{id}/content`

作用：
- 单独获取正文

#### `GET /article/page`

作用：
- 内容广场分页

查询参数：
- `page`
- `size`
- `categoryId`
- `status`

说明：
- 当前公开查询只返回已发布内容

#### `GET /article/my`

作用：
- 获取我的文章

#### `GET /article/follow`

作用：
- 获取关注作者的文章流

说明：
- 当前依赖 Redis 中维护的关注集合

#### `GET /article/latest`

作用：
- 首页刷新获取最新内容

#### `GET /article/more`

作用：
- 加载更多历史内容

#### `PUT /article/{id}/publish`

作用：
- 手动改为发布状态

#### `PUT /article/{id}/unpublish`

作用：
- 手动下架

### 6.2 分类接口

#### `GET /category/list`

作用：
- 获取启用分类分页

#### `GET /category/listEnabled`

作用：
- 获取启用分类全量列表

#### `GET /category/{id}`

作用：
- 获取分类详情

#### `POST /category`

作用：
- 新建分类

#### `PUT /category`

作用：
- 编辑分类

#### `DELETE /category/{id}`

作用：
- 删除分类

### 6.3 文件接口

#### `POST /file/upload`

作用：
- 上传内容图片到 MinIO 公共桶

请求：
- `multipart/form-data`
- `files[]`

返回：
- `List<String>` 图片 URL 列表

## 7. 非功能要求

### 7.1 并发与一致性

- 任务执行前用状态 CAS 抢占：`PENDING -> RUNNING`
- 多次重复入队也不会重复执行成功逻辑
- 定时补偿避免 Redis 临时异常导致任务丢失

### 7.2 存储拆分

- MySQL 存结构化骨架
- MongoDB 存正文
- MinIO 存图片

这样可以避免单表承受长文本和大图片地址列表的负担。

### 7.3 可观测性

- `t_article_audit` 留审核流水
- `t_task_log` 留任务执行日志
- 服务日志打印文章和任务 ID

## 8. 后续建议

建议分三步继续演进：

1. 接入通知中心
   - 审核通过 / 驳回后给用户消息提醒
2. 接入搜索服务
   - 已发布文章异步同步到搜索索引
3. 接入社交 Feed
   - 发布成功后推送到粉丝动态流
