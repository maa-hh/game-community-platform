# Steam 服务接口清单（迁移对照表）

> 服务：`steam-service`（8083）  
> `content-service` 仅保留帖子 / 分区 / 上传；游戏下讨论列表见 `/article/by-game/{appId}`。

## Steam 账号 `/steam/**`（原 `/user/steam/**`）

| 方法 | 旧路径 | 新路径 | 登录 |
|------|--------|--------|------|
| GET | `/user/steam/auth-url` | `/steam/auth-url` | 是 |
| GET | `/user/steam/callback` | `/steam/callback` | 否 |
| GET | `/user/steam/profile` | `/steam/profile` | 是 |
| GET | `/user/steam/users/{userId}/profile` | `/steam/users/by-account/{accountId}/profile` | 是 |
| GET | `/user/steam/library` | `/steam/library` | 是 |
| GET | `/user/steam/users/{userId}/library` | `/steam/users/by-account/{accountId}/library` | 是 |
| GET | `/user/steam/games/{appId}/stats` | `/steam/games/{appId}/stats` | 是 |
| POST | `/user/steam/sync` | `/steam/sync` | 是 |
| DELETE | `/user/steam/unbind` | `/steam/unbind` | 是 |

## 游戏关注 `/steam/follows/**`（原 `/user/game/follows/**`）

| 方法 | 旧路径 | 新路径 | 登录 |
|------|--------|--------|------|
| GET | `/user/game/follows` | `/steam/follows` | 是 |
| GET | `/user/game/follows/check/{appId}` | `/steam/follows/check/{appId}` | 是 |
| POST | — | `/steam/follows/check-batch` | 是（批量查关注状态，body: `{ appIds: number[] }`） |
| POST | `/user/game/follows` | `/steam/follows` | 是 |
| DELETE | `/user/game/follows/{appId}` | `/steam/follows/{appId}` | 是 |
| POST | `/user/game/follows/import-steam` | `/steam/follows/import-steam` | 是 |

## 游戏百科 `/game/**`（迁至 steam-service，路径不变）

| 方法 | 路径 | 登录 |
|------|------|------|
| GET | `/game/discover` | 否 |
| GET | `/game/chart` | 否 |
| GET | `/game/page` | 否 |
| GET | `/game/search` | 否 |
| GET | `/game/{appId}` | 否 |
| GET | `/game/{appId}/reviews` | 否 |
| GET | `/game/{appId}/reviews/mine` | 是 |
| PUT | `/game/{appId}/reviews/mine` | 是 |
| DELETE | `/game/{appId}/reviews/mine` | 是 |

## 游戏讨论（content-service）

| 方法 | 旧路径 | 新路径 | 登录 |
|------|--------|--------|------|
| GET | `/game/{appId}/discussions` | `/article/by-game/{appId}` | 否 |

## 内部 Feign `/feign/steam/**`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/games/{appId}/detail` | 游戏详情 |
| POST | `/games/tags` | 批量游戏标签 |
| POST | `/games/discuss-count/sync` | 同步讨论数 |

## 内部 Feign `/feign/content/**`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/games/discuss-counts` | 按 appId 统计已发布帖子数 |

## 运行时语义

### 游戏详情

- `GET /game/{appId}` 不在请求线程等待 Steam。服务优先返回 Redis、MySQL 与 Mongo 的本地快照；首次访问只有基础目录时返回 `detailReady=false`，并在有界线程池后台补全。
- 已有但过期的详情采用 stale-while-revalidate：先返回旧快照，再异步刷新。一次请求只做一次 Mongo 读取和一次刷新判定，SingleFlight/Redis 锁负责合并并发任务。
- 后台写入完整详情后会清除详情缓存；前端仅在 `detailReady=false` 时做有界轮询。

### Steam 资料与游戏库

- `/steam/profile` 对历史绑定缺失头像的记录提交后台补全任务，接口本身不等待 Steam；返回和落库的头像地址统一升级为 HTTPS。
- `/steam/sync` 只有 Steam 正常响应且缺少 `games`/公开库标识时才提示“游戏库未公开”。网络、超时或鉴权失败统一提示稍后重试，不再误判隐私设置。
- 中文游戏库是主结果，英文名称仅作补充；英文请求失败不会丢弃已经获取的中文游戏库。

### 凭证

- Steam Web API Key、DashScope Key、阿里云 AccessKey/Secret 只允许通过环境变量或密钥管理服务注入，禁止写入仓库、启动脚本默认值或 Java `-D` 命令行参数。
- 部署前参考 `.env.example` 配置变量；轮换泄露凭证后，使用进程检查确认命令行中不存在敏感参数。
