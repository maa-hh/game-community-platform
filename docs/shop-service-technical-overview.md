# shop-service v3 技术方案

## 一致性边界

Redis Lua 只负责高并发入口的库存、限购和请求号原子预占；MySQL 是订单、库存、限购和积分的最终事实源。Redis Key 过期或异常后，按 MySQL 中的订单预占量重建。

订单创建流程：

1. 校验商品快照、用户装扮状态和请求号。
2. Lua 原子校验库存、永久限购、冷却时间或窗口限购，并写入订单状态缓存。
3. MySQL 事务写入 `CREATING` 订单，扣减数据库库存并锁定限购记录，状态转为 `PENDING_PAY`。
4. 任一步失败都释放 Redis 预占；数据库任务会修复异常遗留的 `CREATING` 订单。

支付流程只在 shop-service 本地事务中锁定积分账户、扣减积分、写入唯一流水并把订单转为 `PAID`，同时写入唯一的 `t_shop_delivery_task` 和支付 Kafka Outbox。Kafka 发送失败不会回滚已提交支付，而是由 Outbox 退避重试；发货 worker 调用 user-service，按订单号幂等发放装扮，失败指数退避，超过次数进入死信状态。

## 并发保证

- 库存入口由 Redis Lua 串行化，数据库使用 `stock >= quantity` 原子扣减兜底。
- 限购表按 `(user_id, item_id)` 加行锁，同时维护 `purchased_count` 和 `reserved_count`。
- `ONCE_FOREVER` 在 shop-service 内固定为有效限购 1 次，不依赖前端或单次远程查询。
- `LIMIT_PER_WINDOW` 在 Lua 和数据库侧都按窗口起点重置。
- `COOLDOWN` 在 Lua 中按毫秒时间比较，数据库侧再次校验。
- 订单请求使用 `(user_id, request_id)` 唯一索引，积分流水使用 `(biz_type, biz_ref)` 唯一索引。
- 发货任务使用 `order_no` 唯一索引，user-service 的权益发放记录使用订单号幂等。
- `ONCE_FOREVER` 的 Redis bitmap 只做高并发快速拒绝，MySQL `t_shop_purchase_limit` 和 user-service 归属校验仍是最终兜底，bitmap 丢失不会导致超卖。
- XXL-JOB 负责低频的 Redis 库存/限购对账和订单过期修复；请求主链路不依赖扫描任务。

## 迁移

`sql/shop-v3-migration.sql` 负责基础结构迁移；`sql/shop-v3-data-cutover.sql` 负责存量数据收口：重建订单对应的已购买/预占计数、补齐用户积分账户、补发历史发货任务与支付 Outbox，并删除已经下线的优惠券表。执行顺序已写入 `scripts/db/migrations.order`。

生产部署前必须设置 `GATEWAY_INTERNAL_SECRET`，Gateway 和 shop-service 必须使用同一随机密钥；shop-service 不接受绕过 Gateway 的直接调用。

## 测试场景

`sql/shop-consistency-test-scenarios.sql` 提供商品 101～104 四组可重复测试数据：窗口限购、永久限购、有限库存秒杀、余额不足回滚。压测时必须为每次业务请求生成唯一 `requestId`，并额外重放同一 `requestId` 验证幂等。
