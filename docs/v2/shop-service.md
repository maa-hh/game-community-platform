# shop-service 技术文档

## 1. 服务定位

`shop-service` 提供 `/shop/**`，负责商品定义、库存、限购、积分账户、订单和权益发放编排。历史 `/api/shop/**`、无语义 `list/save` 路径不再新增。

| 项目 | 当前值 |
|---|---|
| 端口 | 8085 |
| 主要依赖 | MySQL、Redis、Kafka、Nacos、XXL-JOB、user-service |
| 事实源 | MySQL 订单、库存、限购和积分流水 |
| 高并发入口 | Redis Lua 预占，MySQL 原子扣减兜底 |

## 2. 公开接口

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/shop/item/page` | 登录 | 商品分页 |
| GET | `/shop/item/{itemId}` | 登录 | 商品详情 |
| POST | `/shop/item` | 管理员 | 新建或更新商品 |
| PUT | `/shop/item/{itemId}/status` | 管理员 | 商品上下架 |
| POST | `/shop/exchange` | 登录 | 原子完成兑换、库存/限购预占和积分扣除 |
| POST | `/shop/order` | 登录 | 创建待支付订单 |
| POST | `/shop/order/pay` | 登录 | 支付订单 |
| GET | `/shop/order/{orderNo}` | 登录 | 查询本人订单 |
| GET | `/shop/order/page` | 登录 | 本人订单分页 |
| POST | `/shop/order/{orderNo}/cancel` | 登录 | 取消待支付订单 |
| GET | `/shop/currency/me` | 登录 | 查询本人积分 |
| POST | `/shop/currency/add` | 管理员 | 增加积分 |
| POST | `/shop/currency/deduct` | 管理员 | 扣减积分 |

写操作中的 `requestId` 由调用方生成；同一用户和同一请求号只能产生一个业务结果。积分变更必须产生唯一流水，禁止直接覆盖余额。

## 3. 一致性边界

1. 校验商品快照、用户状态和请求号。
2. Redis Lua 原子校验库存、永久限购、冷却或窗口限购，并写入预占状态。
3. MySQL 事务写入 `CREATING` 订单、扣减数据库库存、锁定限购记录并转为 `PENDING_PAY`。
4. 失败时释放 Redis 预占；修复任务处理遗留的创建中订单。
5. 支付事务锁定积分账户、扣减积分、写唯一流水、转为 `PAID`，并写入发货任务和 Kafka Outbox。
6. 发货消费者按订单号幂等调用 user-service；重试耗尽进入可观测的失败状态。

关键约束：

- `(user_id, request_id)` 唯一索引防止重复下单。
- `(biz_type, biz_ref)` 唯一索引防止重复积分流水。
- `order_no` 唯一索引防止重复发货。
- Redis 只用于快速预占和限流，MySQL 是最终事实源；Redis 丢失后可按订单预占量重建。
- XXL-JOB 只负责低频对账、库存/限购修复和订单过期处理，不参与主请求事务。

## 4. 数据变更和验证

- 基础结构：`sql/shop-v3-migration.sql`
- 数据收口：`sql/shop-v3-data-cutover.sql`
- 一致性场景：`sql/shop-consistency-test-scenarios.sql`
- 执行顺序：`scripts/db/migrations.order`

```bash
mvn -pl service/shop-service -am test
mvn -DskipTests compile
```

生产必须显式设置 `GATEWAY_INTERNAL_SECRET`、数据库密码、Redis 和 XXL-JOB 凭据；不得依赖仓库中的开发默认值。
