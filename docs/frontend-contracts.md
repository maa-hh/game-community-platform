# 前后端接口与联调契约

本文是前端联调清单，不替代后端 Controller 和 `docs/v2` 服务文档。实际协议以 gateway 和后端代码为准；如果二者与本文不一致，先记录差异并同步修正文档。

## 1. 网关和响应格式

前端默认把 gateway 作为唯一后端入口：开发由 CRACO proxy 转发，生产由站点反代或 `serve-production.js` 代理。普通接口预期返回统一包装：

```ts
interface IDataType<T = unknown> {
  code: number;
  message?: string;
  data?: T;
}
```

`code === 200` 表示业务成功。分页数据保留后端返回的 `page`、`size`、`total`、`pages`、`hasNext` 或 cursor 等元数据；service 只在边界把历史字符串数字归一化，不能把所有分页响应粗暴变成数组。

业务码和 HTTP 状态都可能表示鉴权失败：

| 场景           | 业务码/HTTP            | 前端处理                                |
| -------------- | ---------------------- | --------------------------------------- |
| access 失效    | `40101` 或 HTTP 401    | refresh 单飞，成功后原请求最多重试一次  |
| refresh 失效   | `40102`                | 清理 access/session，发出 auth-required |
| refresh 并发锁 | `429`                  | refresh 请求短暂退避后最多重试三次      |
| 其它错误       | 400/403/404/409/500 等 | 保留结构化 message，交给页面格式化      |

## 2. 领域对照

| 前端 service                    | 公开路径                                  | 后端文档                                                                            |
| ------------------------------- | ----------------------------------------- | ----------------------------------------------------------------------------------- |
| `auth/account/profile/cosmetic` | `/user/**`                                | `docs/v2/user-service.md`                                                           |
| `content`                       | `/article/**`、`/category/**`、`/file/**` | `docs/v2/content-service.md`                                                        |
| `social`                        | `/social/**`、`/report`                   | `docs/v2/social-service.md`                                                         |
| `notification`                  | `/notification/**`                        | `docs/v2/notification-service.md`                                                   |
| `game/steam/userGame`           | `/game/**`、`/steam/**`                   | `docs/v2/steam-service-api-inventory.md`                                            |
| `hotRank`                       | `/hot-article/**`                         | `docs/v2/recommend-service-current.md`；旧 `/list`、`/category/{id}` 由后端兼容保留 |
| `search`                        | `/search/**`                              | `docs/v2/search-service.md`                                                         |
| `shop`                          | `/shop/**`                                | `docs/v2/shop-service.md`                                                           |
| `moderation`                    | `/audit/**`                               | `docs/v2/audit-service.md`                                                          |
| `danmaku`                       | `/danmaku/**`                             | 后端架构文档及 danmaku-service 实现                                                 |

## 3. 认证与 Cookie

### 登录/退出

- 登录：`POST /user/auth/login`，成功返回 access token、过期时间和用户信息；refresh token 通过 Set-Cookie 设置。
- 注册/验证码/重置密码：`/user/auth/send-code`、`/register`、`/reset-password`；这些请求不携带旧 access token。
- 退出：`POST /user/auth/logout`，前端清理本地 access、会话标志并调用 `resetAuthRefreshState()`。
- 刷新：`POST /user/auth/refresh`，空 body，必须 `withCredentials: true`；前端不读取 refresh token。

联调必须检查：

1. gateway CORS 的 `Access-Control-Allow-Credentials` 和明确 origin；
2. refresh Cookie 的 Domain、Path、SameSite、Secure 与前端部署域名匹配；
3. HTTPS 生产环境不要使用不带 Secure 的敏感 Cookie；
4. access 失效时后端返回 `40101` 或 HTTP 401 的规则在前后端一致；
5. 多标签页 refresh 轮换不会因旧 Promise 覆盖新会话。

## 4. 内容、媒体和分片上传

内容发布由 `PostEditor`、`content.ts` 和 `usePersistentPostUploadRecovery` 协作：

```text
文件
  → POST /file/upload/init
  → 多次 POST /file/upload/chunk（uploadId + chunkIndex + file）
  → POST /file/upload/merge
  → POST /file/upload/bind（绑定 publicId）
  → POST/PUT /article
  → POST /article/{id}/publish
```

协议要求：

- init 返回 `uploadId`、`chunkSize`、`totalChunks`、已上传 chunk 列表；
- 单文件并发由 `REACT_APP_UPLOAD_CONCURRENCY` 控制，代码上限为 8；
- chunk 请求使用 180 秒超时和最多 3 次指数退避；
- 失败时保留 upload session，支持 status 查询和下次续传；
- 用户取消或不可恢复错误时调用 abort；
- 合并后必须拿到最终 URL/publicId，再绑定文章；
- 大文件请求的 gateway、MinIO、后端 multipart 限制必须一致；
- 文章正文图片、视频封面和视频文件的 publicId/URL 不能混用。

后端内容文档重点核对：公开文章 ID 与内部数据库 ID、草稿/审核/发布状态、文章正文存储和媒体绑定生命周期。

## 5. 信息流、详情和社交

`social.ts` 负责：

- `/social/feed`：关注信息流，使用 page/cursor 元数据；
- 最新/作者/搜索文章结果：映射为 `LatestPostItem`；
- `/social/article/{id}`：帖子详情，最终以服务端为准；
- `/social/comment/list/{articleId}`、`/social/reply/list/{commentId}`：评论/回复分页；
- `/social/like/**`、`/social/favorite/**`、`/social/share/**`：互动状态与计数；
- `/social/follow/**`：关注/拉黑/状态检查；
- `/social/comment`、`/social/reply`：创建；删除和编辑按后端返回的 public ID；
- `/report`：帖子、评论或用户举报。

一致性要求：

1. 点赞/收藏可乐观更新，但网络失败必须回滚并显示可读提示；
2. 详情和 Feed 的计数通过 `postInteraction`、RTK Query 更新或失效保持一致；
3. 评论创建后不要假设本地生成的 ID，成功后使用服务端返回 ID 重新加载详情；
4. 删除/编辑必须遵循作者、管理员和审核状态权限；
5. 转发必须携带目标 public ID、标题/正文和后端允许的最大长度。

## 6. 通知和 SSE

普通接口：

- `GET /notification/summary`
- `GET /notification/summary/categories`
- `GET /notification/messages`
- `PUT /notification/messages/read-category`
- `PUT /notification/messages/read-all`
- `PUT /notification/feed/read`

SSE 由 `createNotificationEventSource` 建立，前端需要兼容后端历史的数字字符串、`data` 包装和直接通知对象三种事件形状。联调要验证：

- 首帧能否通过网关到达浏览器；
- 代理没有缓存 `text/event-stream`；
- refresh 失效时连接关闭并回到登录门禁；
- 重连不会重复增加未读数，通知 ID/事件 ID 要幂等；
- 审核通过/驳回、评论、点赞、关注等事件能映射到正确 category 和页面路由。

通知页进入时会并行刷新摘要、分类摘要和已展开列表。分类列表使用
`createAsyncThunk.condition` 做请求去重；条件拒绝表示已有请求在执行，不是接口失败。
展开分类时如果发现该分类正在加载，前端复用当前请求并继续执行分类已读，避免把正常的
`Aborted due to condition callback returning false` 提示给用户，也避免红点因竞态残留。

## 7. 游戏、Steam、搜索、商城和审核

| 功能            | 联调重点                                                          |
| --------------- | ----------------------------------------------------------------- |
| 游戏发现/详情   | appId 类型、价格/折扣 null 值、Steam 富 HTML、评价分页            |
| Steam 授权/同步 | 外部授权 URL、同步游标、重复同步、成就图 URL、解绑后的数据清理    |
| 游戏关注        | `/steam/follows` 的新增/删除/批量检查与页面乐观状态               |
| 热榜            | 周期/日期参数、排名顺序、行为分数和空榜 fallback                  |
| 搜索            | 用户数字 accountId 精确搜索、昵称前缀、文章分页、建议词和历史     |
| 商城            | 商品分页、库存、积分余额、兑换幂等、重复购买限制、装备状态        |
| 审核            | 任务领取 CAS、管理员权限、详情字段、通过/驳回原因、通知和文章状态 |
| 弹幕            | videoPublicId、历史分页、WebSocket/SSE 地址和消息权限             |

## 8. 联调验证矩阵

| 层级   | 前端检查                                 | 后端/环境检查                         |
| ------ | ---------------------------------------- | ------------------------------------- |
| 静态   | lint、typecheck、build、diff check       | compile、test                         |
| 路由   | 直接访问 `/game/1`、`/post/id`、未知路由 | 网关不能把页面路由误当 API            |
| 认证   | 登录、刷新、过期、退出、多标签页         | JWT、Cookie、CORS、refresh 轮换       |
| 读接口 | Feed、详情、搜索、游戏、通知、商城       | gateway 路由、响应包装、分页字段      |
| 写接口 | 评论、互动、资料、购买、发帖             | 权限、幂等、事务、错误码              |
| 媒体   | 图片、视频、分片续传、中止、播放         | MinIO、multipart、代理超时和 CORS     |
| 实时   | SSE 首帧、重连、去重、退出清理           | notification outbox、Redis、代理缓冲  |
| 字符集 | 中文标题、评论、通知、搜索               | DB/种子脚本 utf8mb4；前端不做错误转码 |

静态检查全部通过不等于接口一定可用；发布前至少执行后端仓库的真实 gateway 冒烟脚本，并记录环境、数据库迁移和第三方 Steam/MinIO/ES 依赖状态。
