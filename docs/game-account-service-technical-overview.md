# Game Account Service Technical Overview

## 1. 模块定位

`game-account-service` 是社区项目中的“游戏身份与资产域”。它解决的是三类问题：

- 社区用户如何与游戏账号建立一对一绑定关系
- 游戏侧角色、皮肤、道具、签到奖励如何做资源管理和账号发放
- 商城支付成功后，如何把订单异步、安全、幂等地发到对应游戏账号

这次迁移选择的是“资源定义分表、账号持有分表、详情入 Mongo、商城发货消息化解耦”的混合优化方案。

---

## 2. 数据设计

### 2.1 MySQL 表

- `t_game_account`：游戏账号主表，记录账号号、昵称、等级、金币、钻石、段位等
- `t_user_game_bind`：社区用户与游戏账号绑定表，一用户一条，一账号一条
- `t_game_character`：角色元数据
- `t_game_skin`：皮肤元数据
- `t_game_item`：道具元数据
- `t_account_character`：账号拥有角色
- `t_account_skin`：账号拥有皮肤
- `t_account_item`：账号拥有道具及数量
- `t_sign_in_reward`：签到奖励配置
- `t_sign_in_record`：月度签到状态
- `t_game_delivery_record`：商城支付后发货幂等表

### 2.2 Mongo 集合

- `t_character_detail`
- `t_skin_detail`
- `t_item_detail`

Mongo 仅存长文案详情和图集，不承担事务判定职责。这样即使 Mongo 暂时不可用，绑定、发货、签到、背包查询这类主链路仍然主要由 MySQL 保底。

---

## 3. 核心执行链路

### 3.1 绑定与换绑

入口：

- `POST /game-account/bind`
- `PUT /game-account/rebind`

规则：

- 一个社区用户只能绑定一个游戏账号
- 一个游戏账号只能被一个社区用户绑定
- 允许换绑
- 不保留历史绑定记录

一致性策略：

- `t_user_game_bind.user_id` 唯一索引
- `t_user_game_bind.game_account_id` 唯一索引
- 服务层事务内更新绑定关系
- 并发抢绑时由数据库唯一键冲突兜底，再翻译成业务异常

### 3.2 我的资产与资源图鉴

玩家资产查询：

- 直接按绑定的 `game_account_id` 查询 `t_account_character`、`t_account_skin`、`t_account_item`

图鉴查询：

- 先查系统资源元数据
- 再按当前账号补 `owned` 标记

这样做的好处是：

- 图鉴和背包职责分开，前端展示更清晰
- 不需要把全部资源拉到内存再手工比对

### 3.3 签到

入口：

- `POST /game-account/sign-in`

流程：

1. 根据当前登录用户找到绑定游戏账号
2. 以 `game_account_id + year_month` 锁定当月签到记录
3. 校验当天是否已签
4. 查 `t_sign_in_reward` 里对应天数的奖励
5. 在同一事务内发奖励并更新 `t_sign_in_record`

一致性策略：

- `t_sign_in_record(game_account_id, year_month)` 唯一索引
- 查询当前月记录时带 `for update`
- 奖励发放与签到位图更新在一个本地事务中完成

### 3.4 商城支付后异步发货

入口：

- Kafka Topic：`SHOP_ORDER_PAID_TOPIC`
- 消费者：`OrderPaidListener`

流程：

1. 接收 `ShopOrderPaidMessage`
2. 以 `orderNo` 写入 `t_game_delivery_record`
3. 如果订单号已存在，说明已经处理过，直接跳过
4. 解析当前用户绑定的游戏账号
5. 根据 `productType + businessCode + quantity` 发放皮肤或道具
6. 成功则更新发货流水为成功，失败则记录失败原因

一致性策略：

- `t_game_delivery_record.order_no` 唯一索引保证幂等
- 发货和流水状态更新放在同一事务中
- 皮肤类通过唯一关系表保证“拥有即一条”
- 道具类通过原子累加更新数量

---

## 4. 并发与一致性设计

### 4.1 为什么没有额外引入分布式锁

这个模块的关键写操作大多能被数据库唯一索引和事务很好兜住：

- 绑定关系：唯一索引
- 签到：月度记录唯一索引 + 行锁
- 发货：订单号唯一索引
- 角色/皮肤拥有关系：唯一索引
- 道具数量：单行原子累加

因此当前设计优先选择“数据库约束 + 本地事务 + 幂等表”，而不是把所有链路都套上一层额外分布式锁。

### 4.2 为什么详情放 Mongo

角色故事、皮肤主题、道具说明这类字段：

- 文本长
- 结构变化快
- 并不适合频繁参与事务计算

把它们放到 Mongo 后：

- MySQL 元数据表可以保持紧凑
- 后续增加预览图、标签、技能数组等结构更灵活
- 不会拉高主表的查询负担

---

## 5. 前端承接方式

这次不是单独起一个后台前端，而是直接接入现有统一前端：

- 侧边栏新增“游戏资产”
- `Profile` 页面移除旧的“资料里直接写游戏账号”模式
- 玩家页统一在 `GameAccountModulePage` 中完成
- 管理员资源管理也放在同一页面的管理员 Tab 中
- `ShopModulePage` 会提示当前游戏资产商品将发货到哪个绑定账号

这样做的优点是：

- 用户心智统一，不需要切换站点
- 登录态、异常处理、样式系统全部复用
- 管理员和普通用户共享同一套前端，只通过 `user.type` 区分权限入口

---

## 6. 当前实现亮点

### 6.1 主链路已经打通

- 用户能绑定、换绑、解绑游戏账号
- 能看自己的角色、皮肤、道具
- 能看资源图鉴与详情
- 能签到并获得奖励
- 商城支付后可以通过 Kafka 幂等发货
- 管理员能在同一个前端里维护资源和签到奖励

### 6.2 迁移时避免了两类旧问题

第一类是“把游戏账号当作用户资料字段顺手更新”。

现在绑定关系独立建模，不再和昵称、签名、头像审核混在一起。

第二类是“消息发货没有幂等表”。

现在发货落到了单独的 `t_game_delivery_record`，重复消费不会重复发货。

---

## 7. 当前边界与后续可继续优化点

### 7.1 当前边界

- 一个用户只能绑定一个游戏账号
- 换绑不保留历史记录
- 当前商城发货只处理皮肤和道具，角色主要通过后台资源发放或签到奖励进入账号

### 7.2 后续建议

- 如果未来要支持“角色类商品售卖”，可以在商城商品类型中补 `character`
- 如果未来要支持“装备皮肤、使用道具”，可以在本服务继续扩充资产消耗与装备状态接口
- 如果未来需要审计换绑行为，再单独加绑定历史表，不建议现在提前复杂化

---

## 8. 验证情况

当前 `game-account-service` 已补并通过以下测试：

- `GameAccountApplicationTest`
- `GameAccountBindingServiceTest`
- `GameAssetServiceTest`
- `GameResourceAdminServiceTest`
- `GameSignInServiceTest`
- `GameDeliveryServiceTest`
- `ModelContractSmokeTest`

验证命令：

```powershell
$env:JAVA_HOME='D:\JAVAINSTALL\jdk17'
$env:Path='D:\JAVAINSTALL\jdk17\bin;' + $env:Path
& 'D:\apache-maven-3.9.11\bin\mvn.cmd' '-pl' 'service/game-account-service' '-am' '-Dtest=GameAccountApplicationTest,GameAccountBindingServiceTest,ModelContractSmokeTest,GameAssetServiceTest,GameResourceAdminServiceTest,GameSignInServiceTest,GameDeliveryServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' 'test'
```

