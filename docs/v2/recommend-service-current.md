# recommend-service 当前实现基线

> 本文以当前代码为准。旧版 `recommend-service.md` 中的 Kafka Streams 窗口聚合、SSE 推送和 `HotArticleService` 已废弃。

## 数据流

1. `social-service` 在业务事务中写入 `t_social_outbox`。
2. Outbox 发布带 `eventId` 的 `ArticleBehaviorMessage` 到 `article-behavior-events`；弹幕服务以 `danmaku-events` 作为可靠事实流。
3. recommend 用两个独立 Kafka Consumer Group 消费社交行为和弹幕事实；弹幕消费端按 `videoPublicId`（新消息优先使用携带的 `articleId`）转换为 `danmakuDelta=1` 的统一行为事件。
4. 消费端以 `eventId` 写入 `t_article_behavior_event`，唯一键保证重复事件不重复计分；弹幕隐藏会发布稳定 eventId 的负向 `danmakuDelta=-1` 事件。
5. Redis Sorted Set 维护总榜、今日日榜和本周周榜实时投影，增量和重建均使用 Lua 原子操作。
6. 前端主动调用 `/hot-article/rank` 获取榜单，不使用 SSE。

消费失败会有限重试，最终进入 `<topic>.DLT`。下游 content-service 暂时不可用时，消费者抛出异常，不静默丢弃事件。

## Redis

- `recommend:rank:total:{scope}`：总榜
- `recommend:rank:daily:{yyyyMMdd}:{scope}`：日榜
- `recommend:rank:weekly:{yyyy-Www}:{scope}`：周榜
- `scope` 为 `all` 或 `cat:{categoryId}`
- `danmakuDelta` 使用 `COMMENT_WEIGHT`，一条弹幕与一条评论等权。

热度增量和榜单裁剪通过 Redis Lua 脚本原子执行。Redis 是可重建投影，行为事件表和 Kafka 是恢复依据。

## 数据库约束

- `t_article_behavior_event.event_id` 非空且唯一。
- `t_hot_rank_snapshot` 对榜单、周期、分类、排名和文章建立唯一约束。
- 旧库执行 `sql/recommend-hot-rank-idempotency-migration.sql`、`sql/recommend-hot-rank-danmaku.sql`；新库使用 `sql/recommend-hot-rank-behavior.sql` 和 `sql/recommend-hot-rank.sql`。

## 运维要求

- 所有 recommend 实例必须使用相同 Redis、Kafka 和 MySQL 配置。
- `XXL_JOB_ACCESS_TOKEN` 必须显式配置，不使用默认 token。
- 首次部署建议 `RECOMMEND_KAFKA_AUTO_OFFSET_RESET=earliest`，确认回放完成后再按容量策略调整。
- 弹幕历史回填按 `t_danmaku_message.status=1` 且视频帖已发布过滤，使用 `backfill-danmaku-{id}` 幂等事件 ID；已有行为事件表时启动回填仍会补弹幕，不会因为表非空而跳过。
- `hotRankBehaviorBackfillJob` 用于全量重建事实表；上线弹幕迁移后至少执行一次，并随后执行总榜、日榜、周榜重建任务。
- `hotRankDanmakuBackfillJob` 只幂等补入历史可见弹幕并刷新总榜/当前日周榜；生产不建议依赖多实例启动预热，启动预热默认关闭，由 XXL-JOB 统一调度。
- 必须监控 Consumer lag、DLT 数量、行为事件重复数、热榜重建耗时和 Redis 错误率。
