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
