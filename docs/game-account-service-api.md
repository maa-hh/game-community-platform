# Game Account Service API

## 模块概览

`game-account-service` 负责社区用户与游戏账号绑定、角色/皮肤/道具资源库、账号已拥有资产、签到奖励，以及商城支付后的 Kafka 幂等发货。

网关统一入口前缀：

- `http://127.0.0.1:8080/game-account/**`

服务直连地址：

- `http://127.0.0.1:8083/game-account/**`

除特别说明外，所有接口都需要携带：

- `Authorization: Bearer {accessToken}`

管理员接口还要求当前用户 `type = 1`。

---

## 1. 绑定接口

### 1.1 绑定游戏账号

- 路径：`POST /game-account/bind`
- 作用：首次将当前社区用户绑定到一个游戏账号

请求体：

```json
{
  "accountNo": "GA10001"
}
```

返回示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "gameAccountId": 1,
    "accountNo": "GA10001",
    "name": "星野",
    "level": 28
  }
}
```

### 1.2 换绑游戏账号

- 路径：`PUT /game-account/rebind`
- 作用：将当前用户从旧游戏账号换绑到新游戏账号，不保留历史绑定记录

请求体：

```json
{
  "accountNo": "GA10002"
}
```

返回：同绑定接口

### 1.3 解绑游戏账号

- 路径：`DELETE /game-account/unbind`
- 作用：解除当前用户与游戏账号的绑定关系

返回示例：

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

### 1.4 获取当前绑定信息

- 路径：`GET /game-account/me`
- 作用：返回当前用户绑定的游戏账号资料和资产概览

返回字段：

- `gameAccountId`：游戏账号主键
- `bound`：是否已绑定
- `accountNo`：对外展示账号号
- `name`：游戏昵称
- `level`：等级
- `gold`：金币
- `diamond`：钻石
- `currentSeasonRank`：当前段位
- `historySeasonRank`：历史最高段位
- `status`：账号状态
- `updateTime`：最近更新时间

---

## 2. 账号资产接口

### 2.1 我的角色

- 路径：`GET /game-account/assets/characters?page=1&size=12`
- 作用：分页查询当前用户已拥有角色

返回数据项：

- `characterId`
- `characterCode`
- `name`
- `icon`
- `rarity`
- `level`
- `status`
- `obtainTime`

### 2.2 我的皮肤

- 路径：`GET /game-account/assets/skins?page=1&size=12`
- 作用：分页查询当前用户已拥有皮肤

返回数据项：

- `skinId`
- `skinCode`
- `characterId`
- `characterCode`
- `name`
- `icon`
- `rarity`
- `equipStatus`
- `status`
- `obtainTime`

### 2.3 我的道具

- 路径：`GET /game-account/assets/items?page=1&size=12`
- 作用：分页查询当前用户已拥有道具及数量

返回数据项：

- `itemId`
- `itemCode`
- `name`
- `icon`
- `itemType`
- `rarity`
- `quantity`
- `status`
- `lastObtainTime`

---

## 3. 资源图鉴接口

### 3.1 角色图鉴

- 路径：`GET /game-account/resources/characters?page=1&size=12&keyword=&rarity=`
- 作用：查询角色资源库，并标记当前用户是否已拥有

返回数据项：

- `characterId`
- `characterCode`
- `name`
- `title`
- `icon`
- `rarity`
- `elementType`
- `characterType`
- `status`
- `owned`

### 3.2 皮肤图鉴

- 路径：`GET /game-account/resources/skins?page=1&size=12&keyword=&characterId=&rarity=`
- 作用：查询皮肤资源库，并标记当前用户是否已拥有

### 3.3 道具图鉴

- 路径：`GET /game-account/resources/items?page=1&size=12&keyword=&itemType=`
- 作用：查询道具资源库，并标记当前用户是否已拥有

### 3.4 角色详情

- 路径：`GET /game-account/resources/characters/{characterCode}/detail`
- 作用：读取 Mongo 中的角色长文案详情

返回字段：

- `characterCode`
- `story`
- `background`
- `skills`
- `tags`

### 3.5 皮肤详情

- 路径：`GET /game-account/resources/skins/{skinCode}/detail`
- 作用：读取 Mongo 中的皮肤详情

返回字段：

- `skinCode`
- `description`
- `theme`
- `previewImages`
- `tags`

### 3.6 道具详情

- 路径：`GET /game-account/resources/items/{itemCode}/detail`
- 作用：读取 Mongo 中的道具详情

返回字段：

- `itemCode`
- `description`
- `usageTips`
- `sources`
- `tags`

---

## 4. 签到接口

### 4.1 执行签到

- 路径：`POST /game-account/sign-in`
- 作用：对当前已绑定游戏账号执行当日签到，并在同一本地事务中发放奖励

返回字段：

- `signed`
- `yearMonth`
- `dayIndex`
- `signCount`
- `consecutiveDays`
- `rewardName`
- `rewardType`
- `rewardCode`
- `quantity`
- `signTime`

### 4.2 查询签到状态

- 路径：`GET /game-account/sign-in/status`
- 作用：返回当月签到位图和今天是否已签

返回字段：

- `yearMonth`
- `signedToday`
- `signCount`
- `consecutiveDays`
- `signBits`
- `lastSignInDate`
- `signedDays`

### 4.3 查询签到奖励配置

- 路径：`GET /game-account/sign-in/rewards`
- 作用：返回当前启用的签到奖励配置，供前端渲染 7 天或更多天数面板

---

## 5. 管理员资源管理接口

### 5.1 角色管理

- `GET /game-account/admin/characters?page=1&size=10&keyword=&rarity=&status=`
- `POST /game-account/admin/characters`
- `PUT /game-account/admin/characters`
- `DELETE /game-account/admin/characters/{id}`
- `POST /game-account/admin/characters/detail`

角色元数据请求体字段：

- `id`
- `characterCode`
- `name`
- `title`
- `icon`
- `rarity`
- `elementType`
- `characterType`
- `status`
- `sortOrder`
- `version`

角色详情请求体字段：

- `characterCode`
- `story`
- `background`
- `skills`
- `tags`

### 5.2 皮肤管理

- `GET /game-account/admin/skins?page=1&size=10&keyword=&characterId=&rarity=&status=`
- `POST /game-account/admin/skins`
- `PUT /game-account/admin/skins`
- `DELETE /game-account/admin/skins/{id}`
- `POST /game-account/admin/skins/detail`

皮肤元数据字段：

- `id`
- `skinCode`
- `characterId`
- `characterCode`
- `name`
- `icon`
- `rarity`
- `status`
- `sortOrder`
- `version`

皮肤详情字段：

- `skinCode`
- `description`
- `theme`
- `previewImages`
- `tags`

### 5.3 道具管理

- `GET /game-account/admin/items?page=1&size=10&keyword=&itemType=&status=`
- `POST /game-account/admin/items`
- `PUT /game-account/admin/items`
- `DELETE /game-account/admin/items/{id}`
- `POST /game-account/admin/items/detail`

道具元数据字段：

- `id`
- `itemCode`
- `name`
- `itemType`
- `rarity`
- `icon`
- `maxStackCount`
- `status`
- `sortOrder`
- `version`

道具详情字段：

- `itemCode`
- `description`
- `usageTips`
- `sources`
- `tags`

### 5.4 签到奖励管理

- `GET /game-account/admin/sign-in-rewards`
- `POST /game-account/admin/sign-in-rewards`
- `PUT /game-account/admin/sign-in-rewards`
- `DELETE /game-account/admin/sign-in-rewards/{id}`

请求体字段：

- `id`
- `dayIndex`
- `rewardType`
- `businessCode`
- `rewardCode`
- `quantity`
- `rewardName`
- `status`

---

## 6. 与商城协作的内部链路

### 6.1 Kafka 消费

- Topic：`SHOP_ORDER_PAID_TOPIC`
- 消息体：`ShopOrderPaidMessage`

关键字段：

- `orderNo`
- `userId`
- `productType`
- `businessCode`
- `quantity`

### 6.2 发货行为

- `productType = 0`：优惠券类消息直接记成功，不在游戏资产服务发货
- `productType = 2`：向 `t_account_skin` 发皮肤
- `productType = 3`：向 `t_account_item` 发道具

幂等保证：

- `t_game_delivery_record.order_no` 唯一索引
- 重复消费同一订单号时直接跳过

