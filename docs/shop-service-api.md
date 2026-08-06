# shop-service API（v3）

shop-service 只提供 `/shop/**`，不保留 `/api/shop/**`、`list`、`save` 等历史兼容路径。

## 商品

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/shop/item/page` | 登录 | 商品分页 |
| GET | `/shop/item/{itemId}` | 登录 | 商品详情 |
| POST | `/shop/item` | 管理员 | 新建或更新商品 |
| PUT | `/shop/item/{itemId}/status` | 管理员 | 上下架 |

商品价格使用 `BIGINT` 积分；库存 `-1` 表示不限库存；复购策略为 `ONCE_FOREVER`、`UNLIMITED`、`COOLDOWN`、`LIMIT_PER_WINDOW`。

## 兑换与订单

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/shop/exchange` | 登录 | 创建订单并扣除积分 |
| POST | `/shop/order` | 登录 | 创建待支付订单 |
| POST | `/shop/order/pay` | 登录 | 支付订单 |
| GET | `/shop/order/{orderNo}` | 登录 | 查询本人订单 |
| GET | `/shop/order/page` | 登录 | 本人订单分页 |
| POST | `/shop/order/{orderNo}/cancel` | 登录 | 取消待支付订单 |

请求必须携带调用方生成的 `requestId`，同一用户同一请求号只会产生一个订单。

订单状态：`-1` 创建失败、`0` 已取消、`1` 创建中、`2` 待支付、`3` 权益发放中、`4` 已完成。

## 积分

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/shop/currency/me` | 登录 | 查询本人积分 |
| GET | `/shop/currency/{userId}` | 管理员 | 查询用户积分 |
| POST | `/shop/currency/add` | 管理员 | 增加积分 |
| POST | `/shop/currency/deduct` | 管理员 | 扣减积分 |

积分变更必须产生唯一业务流水，禁止直接覆盖余额。
