# 用户模块接口文档

## 1. 模块概览

用户模块当前负责：

- 手机验证码注册
- 账号密码登录
- Access Token 鉴权与 Refresh Token 刷新
- 当前用户资料查询
- 资料异步审核提交流程
- 头像私有待审与公共发布
- 密码修改与会话失效
- 用户公开资料查询
- 账号状态切换与注销

前端默认请求链路：

`Browser -> Vite /api -> Gateway(8080) -> user-service(8081)`

说明：

- JWT 只在 `gateway` 解析
- `user-service` 读取网关透传的用户 Header 恢复上下文
- 头像正式地址由 MinIO 公共桶直接提供，外部可直接访问

## 2. 认证机制

### 2.1 Access Token

- 位置：`Authorization: Bearer {accessToken}`
- 有效期：30 分钟
- 用途：访问受保护接口
- 校验位置：`gateway`

### 2.2 Refresh Token

- 位置：`HttpOnly Cookie`
- Cookie 名：`refreshToken`
- Path：`/`
- 有效期：7 天
- 用途：刷新 Access Token
- 说明：Refresh Token 会轮换，Redis 中只存哈希值，不存明文；轮换时按 `sessionId` 加短 Redis 锁，避免并发刷新同时复用旧 token；刷新和网关鉴权都要求 `user:active-session:{userId}` 仍指向当前 `sessionId`

### 2.3 网关透传 Header

| Header | 含义 |
| --- | --- |
| `X-User-Id` | 当前用户 ID |
| `X-User-Type` | 用户类型，`0` 普通用户，`1` 管理员 |
| `X-Game-Account` | 游戏账号 |
| `X-Session-Id` | 当前登录会话 ID |

## 3. 通用返回结构

### 3.1 普通结果

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

### 3.2 失败结果

```json
{
  "code": 500,
  "message": "错误信息",
  "data": null
}
```

### 3.3 网关认证失败

```json
{
  "code": 401,
  "message": "请先登录",
  "data": null
}
```

### 3.4 分页结果

```json
{
  "code": 200,
  "message": "success",
  "data": [],
  "page": 1,
  "size": 10,
  "total": 0
}
```

## 4. 数据字典

### 4.1 用户类型

| 值 | 含义 |
| --- | --- |
| `0` | 普通用户 |
| `1` | 管理员 |

### 4.2 用户状态

| 值 | 含义 |
| --- | --- |
| `0` | 正常 |
| `1` | 禁用或注销 |

### 4.3 审核状态

| 值 | 含义 |
| --- | --- |
| `0` | 无需审核 / 审核完成 |
| `1` | 审核中 |

### 4.4 审核任务类型

| 值 | 含义 |
| --- | --- |
| `PROFILE` | 资料审核 |
| `AVATAR` | 头像审核 |

### 4.5 审核任务状态

| 值 | 含义 |
| --- | --- |
| `PENDING` | 待审核 |
| `PROCESSING` | 审核线程处理中 |
| `PASSED` | 审核通过 |
| `REJECTED` | 审核拒绝 |
| `FAILED` | 审核执行异常 |

## 5. 接口清单

### 5.1 发送验证码

- 路径：`POST /user/sendCode`
- 认证：否
- 作用：向手机号生成 6 位验证码并写入 Redis，开发阶段直接返回验证码

请求参数：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `phone` | `string` | 是 | 中国大陆手机号，正则：`^1[3-9]\d{9}$` |

请求示例：

```json
{
  "phone": "13900000000"
}
```

返回示例：

```json
{
  "code": 200,
  "message": "success",
  "data": "123456"
}
```

### 5.2 手机号注册

- 路径：`POST /user/register/phone`
- 认证：否
- 作用：使用手机号、验证码和初始资料创建普通用户账号

请求参数：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | `string` | 是 | 昵称，2-32 字符 |
| `password` | `string` | 是 | 密码，6-32 字符 |
| `phone` | `string` | 是 | 手机号 |
| `code` | `string` | 是 | 6 位验证码 |
| `gameAccount` | `string` | 否 | 游戏账号，最长 64 字符 |

返回参数：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data` | `long` | 新用户的公开账号 ID，登录和展示使用该值 |

### 5.3 账号密码登录

- 路径：`POST /user/login/account`
- 认证：否
- 作用：使用账号 ID、密码和用户类型登录，返回 Access Token，并通过 Cookie 下发 Refresh Token

请求参数：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `accountId` | `long` | 是 | 用户账号 ID |
| `password` | `string` | 是 | 登录密码 |
| `type` | `int` | 否 | 用户类型，默认 `0` |

响应头：

- `Set-Cookie: refreshToken=...; HttpOnly; Path=/; SameSite=Lax`

返回参数：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `accessToken` | `string` | Access Token |
| `accessTokenExpireIn` | `long` | 剩余秒数，当前为 `1800` |
| `userId` | `long` | 内部用户主键 ID，仅服务内部关联使用 |
| `accountId` | `long` | 公开账号 ID，前端展示和账号登录使用 |
| `username` | `string` | 用户名 |
| `avatar` | `string` | 当前正式头像公共 URL |
| `type` | `int` | 用户类型 |
| `gameAccount` | `string` | 游戏账号 |
| `auditStatus` | `int` | 审核状态 |

### 5.4 刷新 Access Token

- 路径：`POST /user/token/refresh`
- 认证：否，但要求浏览器自动带上 `refreshToken` Cookie
- 作用：校验 Refresh Token，刷新 Access Token，并轮换 Refresh Token

请求参数：

- 无请求体

返回参数：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `accessToken` | `string` | 新 Access Token |
| `accessTokenExpireIn` | `long` | 过期秒数 |

### 5.5 退出登录

- 路径：`POST /user/logout`
- 认证：是
- 作用：删除当前 Redis Session，清除 Refresh Token Cookie

请求头：

- `Authorization: Bearer {accessToken}`

### 5.6 获取当前登录用户

- 路径：`GET /user/me`
- 认证：是
- 作用：返回当前登录用户完整资料

返回参数：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `long` | 内部用户主键 ID |
| `accountId` | `long` | 公开账号 ID |
| `username` | `string` | 昵称 |
| `avatar` | `string` | 正式头像公共 URL |
| `pendingAvatarUrl` | `string` | 待审核头像临时访问 URL，仅本人查询时返回 |
| `signature` | `string` | 个性签名 |
| `phone` | `string` | 手机号 |
| `status` | `int` | 用户状态 |
| `type` | `int` | 用户类型 |
| `gameAccount` | `string` | 游戏账号 |
| `auditStatus` | `int` | 审核状态 |
| `version` | `int` | 用户正式资料版本号，资料修改时用于乐观锁 |
| `followCount` | `int` | 当前固定返回 `0` |
| `fansCount` | `int` | 当前固定返回 `0` |

返回示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "accountId": 10000,
    "username": "demo7031",
    "avatar": "http://localhost:9000/game-community-public/user-files/public/xxx.webp",
    "pendingAvatarUrl": "http://localhost:9000/game-community-private/user-files/pending/yyy.webp?X-Amz-Algorithm=...",
    "signature": "今天也要赢一局",
    "phone": "13800138000",
    "status": 0,
    "type": 0,
    "gameAccount": "gc_demo",
    "auditStatus": 1,
    "version": 3,
    "followCount": 0,
    "fansCount": 0
  }
}
```

说明：

- 当没有头像待审任务时，`pendingAvatarUrl` 为 `null`

### 5.7 修改密码

- 路径：`PUT /user/password`
- 认证：是
- 作用：校验原密码，修改新密码，并让当前账号全部会话立即失效

请求参数：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `oldPassword` | `string` | 是 | 原密码 |
| `newPassword` | `string` | 是 | 新密码，6-32 字符 |

请求示例：

```json
{
  "oldPassword": "Pass@123",
  "newPassword": "Pass@456"
}
```

### 5.8 修改用户资料

- 路径：`PUT /user/info`
- 认证：是
- 作用：提交资料修改申请，写入审核任务表，异步审核通过后再更新正式资料
- 并发控制：请求体必须带上当前 `version`，后端先用 `version + audit_status` 做乐观锁抢占，再写审核任务，避免并发覆盖写

请求参数：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `version` | `int` | 是 | 当前页面持有的用户资料版本号，来自 `GET /user/me` |
| `username` | `string` | 否 | 昵称，2-32 字符 |
| `signature` | `string` | 否 | 个性签名，最长 120 字符 |
| `phone` | `string` | 否 | 手机号，空字符串表示不修改 |
| `gameAccount` | `string` | 否 | 游戏账号，最长 64 字符 |

请求示例：

```json
{
  "version": 3,
  "username": "小张",
  "signature": "2333456",
  "phone": "13900000000",
  "gameAccount": "gc_20002"
}
```

返回说明：

- 接口提交成功后只表示“进入审核队列”
- 正式资料仍以 `t_user` 已通过版本为准
- 审核通过后异步线程会按任务中的 `userVersion` 再次 CAS 回写 `t_user`
- 审核中不允许再次提交资料或头像
- 如果提交时 `version` 已过期，接口会返回“资料已更新，请刷新页面后重试”

### 5.9 上传头像

- 路径：`POST /user/avatar`
- 认证：是
- 作用：上传头像到 MinIO 私有桶待审区，返回临时访问地址，审核通过后再发布到公共桶并更新 `t_user.avatar`

请求格式：

- `multipart/form-data`

请求参数：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `avatar` | `file` | 是 | 头像文件 |

限制说明：

- 仅支持 `jpg/jpeg/png/webp`
- 文件大小不能超过 2MB

返回参数：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data` | `string` | MinIO 私有桶的临时访问 URL |

返回说明：

- 返回值仅用于“待审核预览”
- 审核通过后，正式头像地址会写入 `t_user.avatar`
- 正式头像地址为 MinIO 公共桶 URL，外部可直接访问

### 5.10 注销账号

- 路径：`POST /user/cancel`
- 认证：是
- 作用：将当前账号置为禁用/注销状态，并清理会话

### 5.11 切换用户状态

- 路径：`PUT /user/status`
- 认证：是
- 作用：修改当前用户状态

请求参数：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `status` | `int` | 是 | 用户状态，当前仅接受 `0` 或 `1` |

### 5.12 根据公开账号 ID 查询详细资料

- 路径：`GET /user/{accountId}`
- 认证：是
- 作用：按公开账号 ID 查询指定用户的详细信息

说明：

- 查询自己时会额外返回 `pendingAvatarUrl`
- 查询他人时手机号会被置空
- 管理员可查看手机号

### 5.13 根据公开账号 ID 查询简版资料

- 路径：`GET /user/simple/{accountId}`
- 认证：是
- 作用：按公开账号 ID 查询指定用户的轻量资料，用于搜索结果卡片和列表展示

返回参数：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `long` | 内部用户主键 ID |
| `accountId` | `long` | 公开账号 ID |
| `username` | `string` | 昵称 |
| `avatar` | `string` | 正式头像公共 URL |
| `signature` | `string` | 个性签名 |
| `gameAccount` | `string` | 游戏账号 |

### 5.14 批量查询用户

- 路径：`GET /user/ids`
- 认证：是
- 作用：按多个用户 ID 批量查询详细资料

请求示例：

`GET /user/ids?ids=1&ids=2&ids=3`

### 5.15 按昵称前缀分页搜索用户

- 路径：`GET /user/simple/search`
- 认证：是
- 作用：根据昵称前缀分页返回用户简版资料

请求参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | `string` | 否 | 昵称前缀 |
| `page` | `int` | 否 | 页码，默认 `1` |
| `size` | `int` | 否 | 每页条数，默认 `10` |

## 6. 存储与审核说明

### 6.1 用户主表 `t_user`

`t_user` 仅保存“正式生效”的用户资料：

- 公开账号 ID
- 用户名
- 个性签名
- 手机号
- 游戏账号
- 正式头像公共 URL
- 用户状态
- 审核状态
- `version`

### 6.2 认证表 `t_user_auth`

`t_user_auth` 保存账号密码校验所需的敏感字段：

- `user_id`
- `password`
- `salt`
- `version`

说明：

- 内部关联使用 `user_id`
- 登录输入使用公开 `accountId` 查到用户后，再按内部 `id` 查询认证记录
- 密码和盐不再放在 `t_user` 中，降低用户主表查询负担，也避免资料查询误带认证字段

### 6.3 审核任务表 `t_user_audit_task`

该表保存待审核任务，不把待审字段冗余塞回 `t_user`。

字段说明：

| 字段 | 说明 |
| --- | --- |
| `user_id` | 所属用户 |
| `task_type` | `PROFILE` 或 `AVATAR` |
| `status` | `PENDING/PROCESSING/PASSED/REJECTED/FAILED` |
| `payload` | 任务负载 JSON |
| `error_message` | 审核拒绝或执行异常原因 |

### 6.4 头像存储策略

- 私有桶：待审核头像
- 公共桶：审核通过后的正式头像
- 待审核头像返回 MinIO 临时访问 URL
- 正式头像直接使用 MinIO 公共 URL

### 6.5 Redis Key

| Key | 说明 | TTL |
| --- | --- | --- |
| `sms:code:{phone}` | 手机验证码 | 5 分钟 |
| `user:session:{sessionId}` | 当前登录会话，包含 refresh token 哈希 | 7 天 |
| `user:active-session:{userId}` | 单端登录指针，指向当前有效 sessionId | 7 天 |
| `user:login:lock:{userId}` | 登录串行锁，避免并发登录覆盖会话指针 | 5 秒 |
| `user:register:lock:{phone}` | 手机号注册并发锁 | 10 秒 |
| `user:refresh:lock:{sessionId}` | refresh token 轮换并发锁 | 5 秒 |

### 6.6 公开账号号

- `t_user.id` 是内部主键，用于表关联、JWT claims、Redis Session 和服务内部查询
- `t_user.account_id` 是公开账号号，从 `10000` 开始递增，用于登录入参、前端展示、玩家搜索和公开主页路径
- 注册接口返回 `account_id`，不是内部主键
- `account_id` 有唯一索引；注册时先拿数据库自增主键 `id`，再在同一事务内回填 `account_id = id + 9999`

## 7. 典型调用顺序

### 7.1 注册并登录

1. `POST /user/sendCode`
2. `POST /user/register/phone`
3. `POST /user/login/account`
4. 浏览器保存 `accessToken`，同时接收 `refreshToken` Cookie

### 7.2 登录态续期

1. 普通业务接口返回 `401`
2. 前端调用 `POST /user/token/refresh`
3. 刷新成功后重试原请求

### 7.3 修改资料

1. `PUT /user/info`
2. 后端先校验 `version`，再用乐观锁把用户 `auditStatus` 从 `0` 抢占为 `1`
3. 新资料和 `userVersion` 写入 `t_user_audit_task`
4. `auditExecutor` 原子认领任务，把状态从 `PENDING` 改为 `PROCESSING`
5. 审核通过后按 `payload.userVersion` 再次 CAS 回写 `t_user`

### 7.4 上传头像

1. `POST /user/avatar`
2. 后端将头像写入 MinIO 私有桶
3. 返回临时访问 URL 给前端预览
4. 后端先用 `version + audit_status` 乐观锁抢占资料审核状态，再把 `userVersion` 写入任务
5. `auditExecutor` 原子认领任务，把状态从 `PENDING` 改为 `PROCESSING`
6. 异步审核该图片 URL
7. 审核通过后复制到 MinIO 公共桶
8. 按 `payload.userVersion` CAS 更新 `t_user.avatar` 为公共 URL

## 8. 备注

- `GET /user/avatar/file/**` 已移除，不再通过 `user-service` 代理图片
- 当前搜索、简版查询、详细查询接口在代码实现中仍要求登录态
- 当前 `followCount`、`fansCount` 仍为占位字段，固定返回 `0`
