# social-service 技术说明

## 模块定位

social-service 承担用户互动层能力，包括评论、回复、点赞、浏览历史、关注、粉丝、黑名单和举报。它不再沿用 demo 模块中“几乎全部写 MongoDB”的方式，而是将可约束、可分页、需要并发一致性的结构化数据迁移到 MySQL，仅保留评论正文这类大文本在 MongoDB 中。

## 存储边界

MySQL 表：

| 表 | 作用 | 关键约束 |
| --- | --- | --- |
| t_social_article_stats | 文章点赞数、评论数、浏览数 | article_id 唯一 |
| t_social_comment | 评论元数据 | article_id/status/create_time 索引 |
| t_social_reply | 回复数据 | comment_id/status/create_time 索引 |
| t_social_article_like | 文章点赞关系 | user_id + article_id 唯一 |
| t_social_comment_like | 评论点赞关系 | user_id + comment_id 唯一 |
| t_social_reply_like | 回复点赞关系 | user_id + reply_id 唯一 |
| t_social_browse_history | 用户浏览历史 | user_id + article_id 唯一 |
| t_social_feed_item | 用户 Feed 信箱 | user_id + article_id 唯一 |
| t_social_follow | 关注关系 | user_id + follow_user_id 唯一 |
| t_social_black | 黑名单关系 | user_id + black_user_id 唯一 |
| t_social_report | 举报工单 | status/create_time 索引 |

MongoDB 集合：

| 集合 | 作用 | 说明 |
| --- | --- | --- |
| social_comment_content | 评论正文 | commentId 唯一索引，避免评论大文本撑大 MySQL 热表 |

回复内容目前放 MySQL，原因是回复长度小、强依赖评论分页、需要和回复点赞计数一起查询。若后续回复体量显著增长，可以用与评论正文相同的方式拆到 MongoDB。

## 核心链路

### 评论发布

1. Gateway 校验 accessToken 并透传用户上下文。
2. social-service 校验文章是否存在。
3. 使用本地 DFA 审核评论文本。
4. 写入 `t_social_comment` 评论元数据。
5. 写入 MongoDB `social_comment_content` 评论正文。
6. 原子增加 `t_social_article_stats.comment_count`。

MySQL 和 MongoDB 没有做分布式事务。当前策略是在同一个业务事务中先插 MySQL，再写 Mongo；Mongo 写失败会抛异常并回滚 MySQL。删除评论时以 MySQL 元数据为可见性准绳，Mongo 清理失败不会导致已删评论重新可见，后续可补偿清理。

### 点赞

1. 插入点赞关系表。
2. 唯一索引拦截重复点赞。
3. 只有首次插入成功时，才原子增加目标计数。
4. 取消点赞时先删除关系，只有删除成功才原子减少计数。

这样可以避免并发重复点击导致计数翻倍，也能避免取消点赞把计数扣成负数。扣减 SQL 使用 `GREATEST(0, count - 1)` 兜底。

### 浏览历史

同一用户浏览同一文章只保留一条历史记录。第一次浏览插入记录并增加浏览数；后续浏览只刷新 `update_time`，不重复增加浏览数。这个策略更接近“独立访客浏览数”，不是 PV。如果后面要做 PV，可以单独增加浏览流水表或 Redis 计数。

### 关注与黑名单

关注关系使用唯一索引实现幂等。拉黑时会同步删除：

- 当前用户关注对方的关系
- 对方关注当前用户的关系

这样可以保证黑名单状态和关注关系不会互相冲突。

黑名单不只影响关系页展示，后端写接口也会强制兜底：

- 关注：任一方向存在黑名单关系时不能再次关注。
- 评论文章：当前用户和文章作者任一方向存在黑名单关系时拒绝。
- 回复评论：当前用户和文章作者、评论作者、被回复用户任一方向存在黑名单关系时拒绝。
- 点赞文章、评论、回复：按目标作者链路逐级校验黑名单，避免绕过前端直接调接口继续互动。
- Feed 推送：作者发布文章时不会推送给黑名单关系中的粉丝。

### Feed 信箱

当前实现采用推拉结合：

1. 文章审核通过发布后，content-service 通过带 `X-Internal-Token` 的 Feign 接口 `/feign/social/feed/publish` 调用 social-service。
2. social-service 查询作者粉丝，将 `authorId/articleId/publishedTime` 写入 `t_social_feed_item`。
3. 用户关注某个作者时，立即拉取该作者最近文章补偿写入信箱，避免“关注后看不到历史内容”。
4. 用户请求 `/social/feed` 时，优先按 `published_time` 游标读取信箱。
5. 如果信箱数据不足，则按当前游标从全部已关注作者回源补齐当前响应，不回写信箱。

Feed 信箱按 `post_type` 独立限容，每个帖子类型最多保留 `ContentConstants.FEED_CAPACITY` 条，超出时淘汰该类型最旧记录。

Feed 信箱只保存文章基础索引，不复制标题、摘要、封面等文章内容，避免内容修改后多处冗余不一致。返回给前端前会通过 content-service 的文章查询接口补齐文章骨架。

### 举报

举报先写入 `t_social_report`，再通过 Kafka 投递 `ReportAuditMessage`，audit-service 消费后幂等写入统一的 `t_moderation_task`。审核端点开详情时才通过 Feign 拉取目标详情，避免 Kafka 消息携带大正文或阻塞消费线程。

目标详情和处理动作：

- 文章：通过 content-service 拉取文章标题、摘要、正文、图片；举报采纳后下架文章。
- 评论：通过 social-service 拉取评论内容；举报采纳后隐藏评论。
- 回复：通过 social-service 拉取回复内容；举报采纳后隐藏回复。
- 用户：通过 user-service 拉取用户资料；举报采纳后封禁用户。

管理员处理时先使用 `claimToken + version + leaseExpireTime` CAS 认领，再提交带 `requestId` 的幂等处理请求；联动目标处理失败时工单回退到待处理，租约过期也会自动回收。通知直接发送 Kafka，由 Producer 配置负责重试，最终失败写入 user-service 的失败记录表。

## 并发与一致性设计

- 唯一索引：所有“一个用户对一个目标只能有一条关系”的数据都用唯一索引兜底，包括点赞、关注、黑名单、浏览历史。
- 原子计数：统计字段不在 Java 内存里读出再写回，而是直接执行 `count = count + 1` 或 `count = GREATEST(0, count - 1)`。
- 乐观锁字段：评论、回复、统计、举报表都保留 `version` 字段，适合后续管理端编辑、隐藏、恢复等复杂更新。
- 逻辑删除：评论和回复保留 `deleted` 字段，用户侧删除后不再可见，同时保留审计可能性。
- 快照字段：评论、回复保存 `username/avatar` 快照，避免列表查询每条都强依赖 user-service。关系列表仍实时调用 user-service 聚合当前用户资料。

## 服务依赖

- user-service：用于批量获取用户信息，接口为 `/user/ids`。
- content-service：用于校验和读取文章骨架，接口为 `/article/listByIds`，避免社交链路触发文章正文 Mongo 读取；文章发布成功后会调用 social-service 内部 Feed 推送接口，social-service 也会通过 `/article/author/{authorId}/published` 和 `/article/authors/published` 做关注补偿与回源拉取。
- gateway：负责 accessToken 校验、session 状态校验、用户上下文 Header 透传。
- MongoDB：只保存评论正文。
- MySQL：保存社交结构化数据。

## 前端改造

- 新增 `frontend/src/api/social.ts`，封装评论、点赞、关注、浏览和黑名单接口。
- 新增 `SocialModulePage`，展示关注、粉丝、黑名单、浏览历史和点赞文章。
- 文章详情页新增社交面板，支持展示浏览/评论/点赞统计、点赞文章和发表评论；当前用户拉黑作者后，页面会禁用点赞、评论和回复入口。
- 文章详情页进入时调用 `viewArticle` 记录浏览，展示作者头像昵称，并支持进入作者主页、关注、拉黑。
- 用户主页支持关注/拉黑操作，展示 TA 关注的人和 TA 的粉丝。
- 内容广场新增“发现 / 关注流”切换、更新按钮和发现更多按钮。
- 审核中心支持点开举报工单查看目标详情，并可跳转文章详情或用户主页辅助判断。
- Dashboard 中社交模块状态从 planned 改为 ready。

## 取舍与后续优化

- 当前未引入 Kafka 通知链路，避免本地启动必须依赖 Kafka。后续通知服务稳定后，可以在关注、评论、回复、点赞动作成功后投递领域事件。
- 当前评论审核只接入本地 DFA，避免社交链路被外部模型延迟拖慢。后续可和 content-service 共用 Spring AI Alibaba 审核客户端，做异步审核或先发后审。
- 当前浏览数按 UV 统计，不按 PV 统计。若运营侧需要 PV，可以新增 `t_social_article_view_log` 或 Redis HyperLogLog/计数器。
