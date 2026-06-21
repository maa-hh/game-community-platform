# 用户模块技术说明

## 1. 模块定位

用户模块是项目的身份中心、资料中心和账号安全基础设施，负责：

- 把游客转成受控用户
- 为网关鉴权提供用户会话依据
- 为业务服务提供用户上下文
- 维护用户资料与头像
- 通过审核机制治理用户生成内容

它连接的核心组件有：

- `frontend`
- `gateway`
- `user-service`
- `Redis`
- `MySQL`
- `MinIO`
- `AuditClient`

## 2. 整体脉络

### 2.1 认证链路

当前登录态模型已经升级为：

- `accessToken`：30 分钟，走 `Authorization`
- `refreshToken`：7 天，走 `HttpOnly Cookie`
- `sessionId`：单端登录的服务端会话标识
- Redis Session：保存当前有效登录态和 refresh token 哈希
- Refresh Lock：刷新登录态时按 `sessionId` 加短 Redis 锁
- `accountId`：对外展示和登录使用的账号号
- `userId`：内部主键，只用于服务关联、JWT claims 和 Redis Session

链路分工：

1. 前端请求到网关
2. 网关解析 `accessToken`
3. 网关校验 Redis Session 是否有效
4. 网关校验 `user:active-session:{userId}` 是否仍指向当前 `sessionId`
5. 网关把 `userId/type/gameAccount/sessionId` 透传给 `user-service`
6. `UserFilter` 恢复 ThreadLocal 用户上下文
7. `@LoginCheck` / `@AdminCheck` 基于上下文做权限判断

这里的关键点是：

- JWT 不在 `user-service` 内直接解析
- `user-service` 只消费“网关已经验证过的用户上下文”

### 2.2 资料审核链路

资料修改不再是同步 CRUD，而是“提交任务 -> 异步审核 -> 审核通过后生效”：

1. 用户调用 `PUT /user/info`
2. `user-service` 先做基础校验，例如手机号唯一性、字段长度
3. 后端校验前端提交的 `version`
4. 使用 `version + audit_status` 的 CAS，把用户状态从“空闲”抢占为 `AUDITING`
5. 新资料和 `userVersion` 写入 `t_user_audit_task.payload`
6. `auditExecutor` 原子认领任务，把状态从 `PENDING` 改为 `PROCESSING`
7. 线程池异步执行文本审核
8. 审核通过后按 `payload.userVersion` 再次 CAS 回写 `t_user`
9. 审核失败或执行异常则按版本 CAS 清理 `audit_status`

这条链路把“待审核内容”和“正式生效内容”分开了。

### 2.3 头像审核链路

头像处理采用了“私有待审 -> 公共发布”的两阶段存储：

1. 用户上传头像
2. 文件写入 MinIO 私有桶待审区
3. 返回一个预签名临时 URL 给前端预览
4. 后端先用 `version + audit_status` CAS 抢占资料审核状态，并把 `userVersion` 写入任务
5. 后端异步线程原子认领任务，把状态从 `PENDING` 改为 `PROCESSING`
6. 重新生成临时 URL，并交给审核服务校验
7. 审核通过后，把对象复制到 MinIO 公共桶
8. 按 `payload.userVersion` CAS 把公共 URL 写回 `t_user.avatar`
9. 删除旧头像和私有待审对象

这套模型的意义是：

- 待审核图片不会提前暴露为公共资源
- 正式头像一旦通过，外部可以直接访问 MinIO 公共地址
- `user-service` 不再承担图片代理下载责任

## 3. 数据结构设计

### 3.1 `t_user`

`t_user` 只保留正式用户主数据：

- `id`
- `account_id`
- `username`
- `signature`
- `phone`
- `game_account`
- `avatar`
- `status`
- `type`
- `audit_status`
- `last_login_time`
- `version`

这是“当前生效版本”的单一事实来源。`id` 是内部主键，`account_id` 是对外账号号；外部登录和展示都走 `account_id`，服务内部关联仍然走 `id`。

### 3.2 `t_user_auth`

新增 `t_user_auth` 用于承载认证敏感字段。

主要字段：

- `user_id`
- `password`
- `salt`
- `version`
- `create_time`
- `update_time`

拆表原因：

1. 常规资料查询不需要读取密码和盐
2. 认证字段安全等级高，单独管理更清晰
3. 后续如果接入第三方登录、多因子认证、密码版本号，可以在认证表扩展，不挤压用户主表

### 3.3 `t_user_audit_task`

新增 `t_user_audit_task` 用于承载待审核内容。

主要字段：

- `user_id`
- `task_type`
- `status`
- `payload`
- `error_message`
- `create_time`
- `update_time`

为什么单独建表而不是把所有待审字段塞回 `t_user`：

1. 避免正式字段和待审字段混在一起
2. 避免为了异步审核给 `t_user` 增加大量 `pending_*` 冗余列
3. 让资料审核和头像审核共用一套任务模型
4. 为后续增加审核结果查询、重试、审计留空间

### 3.4 MinIO 双桶模型

当前 MinIO 配置拆成了：

- `publicBucketName`
- `privateBucketName`
- `publicFilePrefix`
- `privateFilePrefix`
- `presignedExpireSeconds`

职责边界：

- 私有桶：审核前对象
- 公共桶：审核通过后的正式对象

## 4. 功能亮点

### 4.1 登录态安全模型更完整

当前方案相较于单 JWT 有明显提升：

- Access Token 短期化
- Refresh Token 独立用途
- Redis Session 支持服务端失效控制
- `active-session` 指针保证旧会话即使残留也无法继续通过网关
- Refresh Token 哈希存储
- Token Rotation
- 登录串行锁避免并发登录覆盖会话指针
- Refresh Token 轮换时按 `sessionId` 加锁，避免并发刷新同时复用旧 token
- 改密码、登出、封禁可立即失效

### 4.2 用户资料真正采用乐观锁

这次不是只靠 `audit_status` 挡重复提交，而是把 `version` 字段正式引入到了写路径：

- `t_user.version` 负责保护正式资料写入
- `t_user_auth.version` 负责保护密码修改写入
- `PUT /user/info` 必须带 `version`
- 资料审核提交、头像审核提交、审核结果回写、审核失败清理，全部围绕 `version` 做 CAS

这样解决了两个问题：

- 页面拿着旧资料再次提交时，不会把新资料覆盖掉
- 异步审核结果回写时，如果用户状态已经被别的写操作推进，也不会盲目覆盖
### 4.3 公开账号号和内部主键分离

这次没有把 MySQL 自增主键初始值改大，而是新增了 `account_id`：

- `id` 保持内部主键，继续承担表关联、JWT claim、Redis Session 的稳定身份
- `account_id` 作为公开账号号，从 `10000` 开始递增，给前端展示、登录输入、玩家搜索使用

这个取舍更稳。直接把主键初始值调大虽然改动少，但仍然是在暴露内部主键；以后如果要分库分表、迁移数据、合并账号或做风控，公开账号号和内部主键分开会舒服很多。

实现上没有再用“查最大值 + 1”发号，而是先插入用户拿到数据库自增 `id`，再在同一事务内回填 `account_id = id + 9999`。这样公开账号号从 `10000` 起步，同时避免了发号锁、抢号和并发计算下一个账号号的问题。

### 4.4 一个审核线程池承接两类任务

当前没有拆两个线程池，而是：

- 头像审核进 `auditExecutor`
- 资料审核也进 `auditExecutor`

这样做的好处是：

- 结构简单
- 配置集中
- 现阶段负载可控

如果后续头像审核量明显大于文本审核，再拆成 `image` / `text` 两个池会更合适。

### 4.5 用任务表解决字段冗余问题

这是这次改造最核心的结构优化之一。

如果把待审用户名、待审签名、待审手机号、待审头像 URL 全部塞回 `t_user`，会带来：

- 表字段膨胀
- 业务含义混乱
- 查询时难以区分“正式值”和“待审值”

现在把这些内容放进 `t_user_audit_task.payload` 后：

- `t_user` 只存正式数据
- `t_user_audit_task` 只存待审核任务
- 表意清晰，扩展也更稳

### 4.6 正式头像走公共直链

审核通过后，头像 URL 直接指向 MinIO 公共桶。

这让：

- 前端加载头像更直接
- `user-service` 不再成为图片流量代理
- 静态资源访问路径更统一

## 5. 难点与取舍

### 5.1 异步审核带来的状态管理

资料和头像一旦异步化，就不能再简单地“更新即生效”。必须处理：

- 审核中是否允许重复提交
- 审核失败如何恢复状态
- 审核异常如何保证用户不会永久卡在 `AUDITING`

当前方案的处理方式是：

- 提交时按 `version + audit_status` 抢占 `audit_status`
- 异步执行时原子认领 `t_user_audit_task`
- 失败或异常时按版本 CAS 清理 `audit_status`
- 审核通过时按任务携带的 `userVersion` CAS 回写正式资料
- 审核结果写回任务表状态

任务状态从 `PENDING` 原子更新为 `PROCESSING` 后才执行审核，因此即使线程池重复调度、服务重试或未来接入 MQ，也不会出现同一个审核任务被多个执行者同时处理并互相覆盖结果。

### 5.5 Refresh Token 并发轮换

Refresh Token Rotation 有一个常见边界：同一个浏览器如果同时发起两个刷新请求，两个请求可能都带着旧 refresh token。如果只做“读 Redis -> 比对哈希 -> 写新哈希”，并发窗口内两个请求都可能通过。

当前处理方式是：

- 从 refresh token claims 中解析 `sessionId`
- 对 `user:refresh:lock:{sessionId}` 设置 5 秒短锁
- 拿到锁的请求才继续读取 Redis Session 并校验 token 哈希
- 写入新 refresh token 哈希后释放锁
- 没拿到锁的请求返回“登录态刷新中，请稍后重试”

这样可以保证单个 `sessionId` 上同一时间只有一个 refresh token 轮换动作生效。

### 5.2 头像预览与正式头像切换

头像现在分成两个地址：

- `pendingAvatarUrl`：临时预览
- `avatar`：正式公共地址

前端需要优先显示 `pendingAvatarUrl`，否则用户上传后会因为正式头像尚未更新而看不到刚提交的图。

### 5.3 资料字段的最终一致性

资料更新现在是“最终一致”，不是“强同步”：

- 接口返回成功只代表任务已提交
- 真正生效依赖异步审核线程

这会带来前端体验与后端状态设计上的变化：

- 文案要提示“进入审核”
- 审核中需要禁止重复编辑
- 查询接口要明确展示当前 `auditStatus`

### 5.4 公共桶与私有桶的权限边界

双桶模型的关键不只是“多一个配置”，而是权限语义：

- 私有桶不能公开读
- 公共桶需要公开读策略
- 待审核 URL 必须通过预签名生成

如果这里配置不严，很容易退化成“看起来私有，实际上还是公开可读”。

## 6. 当前模块的整体功能

到目前为止，用户模块已经形成了下面这条完整主线：

1. 注册登录
2. 长短期 Token 会话管理
3. 密码修改与会话失效
4. 用户资料查询
5. 资料异步审核
6. 头像私有待审和公共发布
7. 搜索与公开资料查询

它已经不是一个简单的用户表 CRUD，而是一套有审核、有会话、有文件存储边界的用户基础设施。

## 7. 后续建议

### 7.1 审核结果可视化

后续可以增加：

- 我的审核记录
- 最近一次审核失败原因
- 头像/资料审核历史

当前 `t_user_audit_task` 已经为这类能力留好了位置。

### 7.2 审核线程池分拆

如果后续头像审核量上来，可以考虑拆成：

- `auditTextExecutor`
- `auditImageExecutor`

### 7.3 会话与设备管理

当前单端登录已经基于 `sessionId` 建好基础，后续可以平滑升级为：

- 多端登录
- 设备列表
- 异地登录提醒
- 强制下线指定设备

## 8. 总结

这次改造后，用户模块的主结构更清晰了：

- `t_user` 只管正式资料
- `t_user_auth` 只管密码和盐
- `t_user_audit_task` 只管待审核任务
- `auditExecutor` 统一承接资料和头像异步审核
- MinIO 用私有桶承接待审核对象，用公共桶承接正式头像
- 前端直接访问正式公共头像，不再依赖后端图片代理
- 公开账号号 `account_id` 和内部主键 `id` 分离

这让模块的职责边界、安全性和可演进性都比之前更稳。
