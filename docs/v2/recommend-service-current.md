# recommend-service 当前实现基线

> 本文以当前代码为准。旧版 `recommend-service.md` 中的 Kafka Streams 窗口聚合、SSE 推送和 `HotArticleService` 已废弃。

## 数据流

1. `social-service` 在业务事务中写入 `t_social_outbox`。
2. Outbox 发布带 `eventId` 的 `ArticleBehaviorMessage` 到 `article-behavior-events`。
3. recommend 使用一个 Kafka Consumer 消费原始行为事件。
4. 消费端以 `eventId` 写入 `t_article_behavior_event`，唯一键保证重复事件不重复计分。
5. Redis Sorted Set 维护总榜、今日日榜和本周周榜实时投影。
6. 前端主动调用 `/hot-article/rank` 获取榜单，不使用 SSE。

消费失败会有限重试，最终进入 `<topic>.DLT`。下游 content-service 暂时不可用时，消费者抛出异常，不静默丢弃事件。

## Redis

- `recommend:rank:total:{scope}`：总榜
- `recommend:rank:daily:{yyyyMMdd}:{scope}`：日榜
- `recommend:rank:weekly:{yyyy-Www}:{scope}`：周榜
- `scope` 为 `all` 或 `cat:{categoryId}`

热度增量和榜单裁剪通过 Redis Lua 脚本原子执行。Redis 是可重建投影，行为事件表和 Kafka 是恢复依据。

## 数据库约束

- `t_article_behavior_event.event_id` 非空且唯一。
- `t_hot_rank_snapshot` 对榜单、周期、分类、排名和文章建立唯一约束。
- 旧库执行 `sql/recommend-hot-rank-idempotency-migration.sql`，新库使用 `sql/recommend-hot-rank-behavior.sql` 和 `sql/recommend-hot-rank.sql`。

## 运维要求

- 所有 recommend 实例必须使用相同 Redis、Kafka 和 MySQL 配置。
- `XXL_JOB_ACCESS_TOKEN` 必须显式配置，不使用默认 token。
- 首次部署建议 `RECOMMEND_KAFKA_AUTO_OFFSET_RESET=earliest`，确认回放完成后再按容量策略调整。
- 必须监控 Consumer lag、DLT 数量、行为事件重复数、热榜重建耗时和 Redis 错误率。
