# 购物模块接口文档

## 模块说明

购物模块由 `shop-service` 提供，Gateway 路由前缀为 `/shop/**`，同时兼容 demo 中的 `/api/shop/**` 路径。普通用户接口需要携带 `Authorization: Bearer <accessToken>`，后台管理接口需要管理员权限。

核心链路：创建订单时先用 Redis + Lua 做库存、限购、幂等和订单号生成，再投递到 Redis 队列异步落库；支付时扣减用户货币、更新订单、标记优惠券使用并发送 Kafka 支付成功事件。

## 商品接口

### 分页查询商品

- 路径：`GET /shop/item/page`
- 兼容路径：`GET /shop/item/list`，`GET /api/shop/item/list`
- 作用：分页查询商品列表。
- 请求参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| page / pageNum | number | 否 | 页码，默认 `1` |
| size / pageSize | number | 否 | 每页数量，默认 `10` |
| productType | number | 否 | 商品类型 |
| status | number | 否 | `1` 上架，`0` 下架 |

### 查询商品详情

- 路径：`GET /shop/item/{itemId}`
- 兼容路径：`GET /api/shop/item/{itemId}`
- 作用：查询商品详情。

### 保存商品

- 路径：`POST /shop/item/admin`
- 兼容路径：`POST /shop/item/save`，`POST /api/shop/item/save`
- 权限：管理员
- 请求体：

```json
{
  "id": 1,
  "name": "星辉头像框",
  "description": "个人主页专属装饰",
  "icon": "https://dummyimage.com/600x360/d8f7e4/24533e&text=Avatar+Frame",
  "price": 30,
  "productType": 3,
  "stock": 50,
  "businessCode": "avatar_frame_star",
  "quantity": 1,
  "limitCount": 2,
  "beginTime": "2026-05-30T10:00:00",
  "endTime": "2026-06-30T10:00:00"
}
```

### 修改商品状态

- 路径：`POST /shop/item/admin/{itemId}/status?status=1`
- 兼容路径：`POST /shop/item/status`，`POST /shop/item/onShelf/{itemId}`，`POST /shop/item/offShelf/{itemId}`
- 权限：管理员
- 作用：上架或下架商品。

## 优惠券接口

### 查询优惠券模板

- 路径：`GET /shop/coupon/list`
- 权限：管理员
- 请求参数：`pageNum`、`pageSize`、`status`
- 作用：查询后台维护的优惠券模板。

### 查询优惠券详情

- 路径：`GET /shop/coupon/{couponId}`
- 权限：管理员

### 保存或更新优惠券

- 路径：`POST /shop/coupon/save`，`POST /shop/coupon/update`
- 权限：管理员
- 请求体：

```json
{
  "id": 1,
  "name": "满30减5券",
  "discountType": 1,
  "discountValue": 5,
  "minAmount": 30,
  "scopeType": -1,
  "expireTime": "2026-12-31T23:59:59",
  "status": 1
}
```

### 删除优惠券模板

- 路径：`DELETE /shop/coupon/{couponId}`
- 权限：管理员

### 发放优惠券

- 路径：`POST /shop/coupon/grant?userId=4&couponId=1`
- 权限：管理员
- 作用：给指定用户发放一张优惠券。

## 用户优惠券接口

### 查询我的优惠券

- 路径：`GET /shop/user-coupon/list`
- 请求参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| pageNum / page | number | 否 | 页码 |
| pageSize / size | number | 否 | 每页数量 |
| status | number | 否 | `0` 未使用，`1` 已使用，`2` 已过期，`3` 锁定中 |
| itemId | number | 否 | 按商品可用性过滤 |

### 查询我的券详情

- 路径：`GET /shop/user-coupon/{userCouponId}`

### 删除用户券

- 路径：`DELETE /shop/user-coupon/{userCouponId}?userId=4`
- 权限：管理员

## 订单接口

### 创建订单

- 路径：`POST /shop/order`
- 兼容路径：`POST /shop/order/create`
- 作用：Redis + Lua 预扣库存和限购，异步生成订单。
- 请求体：

```json
{
  "itemId": 1,
  "quantity": 1,
  "payType": 1,
  "requestId": "6b8120ed-2d24-4d44-a8d5-cfc204d7a447",
  "userCouponId": 9
}
```

- 输出核心字段：

| 字段 | 说明 |
| --- | --- |
| orderNo | 订单号 |
| totalPrice | 原价总额 |
| discountAmount | 优惠金额 |
| finalPrice | 实付金额 |
| couponId / userCouponId | 使用的券 |
| status | `1` 创建中，`2` 待支付，`3` 已支付，`0` 已取消，`4` 失败 |

### 查询订单详情

- 路径：`GET /shop/order/{orderNo}`

### 分页查询我的订单

- 路径：`GET /shop/order/page`
- 请求参数：`page`、`size`

### 支付订单

- 路径：`POST /shop/order/pay`
- 请求体：

```json
{
  "orderNo": "SO5502C9A2D24944CFA7F172FAD75A"
}
```

### 取消订单

- 路径：`POST /shop/order/{orderNo}/cancel`
- 兼容路径：`POST /shop/order/cancel/{orderNo}`

## 用户货币接口

### 查询我的货币账户

- 路径：`GET /shop/currency/me`
- 兼容路径：`GET /api/shop/user-currency/me`

### 查询指定用户货币账户

- 路径：`GET /shop/currency/{userId}`
- 兼容路径：`GET /api/shop/user-currency/{userId}`
- 权限：管理员

### 保存或重置货币账户

- 路径：`POST /shop/currency/save`，`POST /shop/currency/reset`
- 兼容路径：`POST /api/shop/user-currency/save`，`POST /api/shop/user-currency/reset`
- 权限：管理员

### 增减货币

- 路径：`POST /shop/currency/add-gold`
- 路径：`POST /shop/currency/add-diamond`
- 路径：`POST /shop/currency/deduct-gold`
- 路径：`POST /shop/currency/deduct-diamond`
- 兼容路径：同名 `/api/shop/user-currency/**`
- 权限：管理员
- 请求参数：`userId`、`amount`

### 删除货币账户

- 路径：`DELETE /shop/currency/{userId}`
- 兼容路径：`DELETE /api/shop/user-currency/{userId}`
- 权限：管理员

## 状态与类型

| 类型 | 值 | 说明 |
| --- | --- | --- |
| payType | `0` | 金币 |
| payType | `1` | 钻石 |
| productType | `0` | 优惠券商品，支付成功后按商品 `businessId` 发放对应优惠券模板 |
| productType | `2` | 皮肤或体验卡 |
| productType | `3` | 道具或社区权益 |
| coupon status | `0` | 未使用 |
| coupon status | `1` | 已使用 |
| coupon status | `2` | 已过期 |
| coupon status | `3` | 锁定中 |
| discountType | `1` | 立减 |
| discountType | `2` | 折扣，`80` 表示 8 折 |
| minAmount | `0` 或正数 | 最低使用金额，低于门槛时前端不展示、后端拒绝使用 |
| scopeType | `-1` | 全部商品可用，兼容旧字段 |
| scopeItemId | 商品 ID | 指定商品可用，优先级最高 |
| scopeProductType | 商品类型 | 指定商品类型可用 |
