# social-service 接口文档

## 统一说明

- 服务端口：`8084`
- 网关路径：`/social/**`、`/report/**`
- 登录态：除特别说明外，接口需要经过 Gateway，Gateway 校验 accessToken 后透传 `X-User-Id`、`X-User-Type`、`X-Session-Id`。
- 响应格式：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

- 分页响应额外包含：

```json
{
  "code": 200,
  "message": "success",
  "data": [],
  "page": 1,
  "size": 20,
  "total": 0
}
```

## 评论接口

### 新增评论

- 路径：`POST /social/comment`
- 作用：对文章发表评论。评论元数据写 MySQL，评论正文写 MongoDB。
- 登录：需要
- 黑名单规则：评论者与文章作者任一方向存在黑名单关系时，后端拒绝评论。

请求参数：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| articleId | Long | 是 | 文章 ID |
| content | String | 是 | 评论内容，最多 2000 字 |

输出：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| data | Long | 新评论 ID |

### 删除评论

- 路径：`DELETE /social/comment/{commentId}`
- 作用：评论作者删除自己的评论，逻辑删除评论元数据并清理 Mongo 评论正文。
- 登录：需要

路径参数：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| commentId | Long | 评论 ID |

输出：`data = null`

### 评论分页

- 路径：`GET /social/comment/list/{articleId}`
- 作用：分页查询文章评论。
- 登录：可选；登录时会返回当前用户是否点赞。

请求参数：

| 字段 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| articleId | Long | 是 | - | 文章 ID |
| page | Long | 否 | 1 | 页码 |
| size | Long | 否 | 20 | 每页数量，最大 100 |

输出 `data[]`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | Long | 评论 ID |
| articleId | Long | 文章 ID |
| userId | Long | 评论用户 ID |
| username | String | 评论用户昵称快照 |
| avatar | String | 评论用户头像快照 |
| content | String | 评论正文 |
| likeCount | Long | 点赞数 |
| replyCount | Long | 回复数 |
| liked | Boolean | 当前用户是否点赞 |
| createTime | String | 评论时间 |

## 回复接口

### 新增回复

- 路径：`POST /social/reply`
- 作用：对评论回复，回复内容直接存 MySQL。
- 登录：需要
- 黑名单规则：回复者与文章作者、评论作者、被回复用户任一方向存在黑名单关系时，后端拒绝回复。

请求参数：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| commentId | Long | 是 | 评论 ID |
| replyToUserId | Long | 否 | 被回复用户 ID |
| content | String | 是 | 回复内容，最多 1000 字 |

输出：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| data | Long | 新回复 ID |

### 删除回复

- 路径：`DELETE /social/reply/{replyId}`
- 作用：回复作者删除自己的回复。
- 登录：需要

### 回复分页

- 路径：`GET /social/reply/list/{commentId}`
- 作用：分页查询评论下的回复。
- 登录：可选

请求参数：

| 字段 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| commentId | Long | 是 | - | 评论 ID |
| page | Long | 否 | 1 | 页码 |
| size | Long | 否 | 20 | 每页数量 |

输出 `data[]`：`id`、`commentId`、`articleId`、`userId`、`username`、`avatar`、`replyToUserId`、`replyToUsername`、`content`、`likeCount`、`liked`、`createTime`。

## 点赞接口

### 文章点赞 / 取消点赞

- 点赞：`POST /social/like/article/{articleId}`
- 取消：`DELETE /social/like/article/{articleId}`
- 作用：对文章点赞或取消点赞。
- 登录：需要
- 黑名单规则：点赞者与文章作者任一方向存在黑名单关系时，后端拒绝点赞。
- 并发策略：`t_social_article_like` 使用 `(user_id, article_id)` 唯一索引保证重复点赞幂等，统计表使用 SQL 原子增减。

### 评论点赞 / 取消点赞

- 点赞：`POST /social/like/comment/{commentId}`
- 取消：`DELETE /social/like/comment/{commentId}`
- 登录：需要
- 黑名单规则：点赞者与文章作者或评论作者任一方向存在黑名单关系时，后端拒绝点赞。

### 回复点赞 / 取消点赞

- 点赞：`POST /social/like/reply/{replyId}`
- 取消：`DELETE /social/like/reply/{replyId}`
- 登录：需要
- 黑名单规则：点赞者与文章作者、回复作者、被回复用户任一方向存在黑名单关系时，后端拒绝点赞。

### 点赞状态检查

- 文章：`GET /social/like/article/check/{articleId}`
- 评论：`GET /social/like/comment/check/{commentId}`
- 回复：`GET /social/like/reply/check/{replyId}`
- 输出：`data = true/false`

## 文章社交数据接口

### 记录浏览

- 路径：`GET /social/article/{articleId}`
- 作用：读取文章骨架并记录浏览历史。
- 登录：需要
- 并发策略：`t_social_browse_history` 使用 `(user_id, article_id)` 唯一索引，同一用户重复浏览只更新时间，不重复增加浏览数。

### 获取文章统计

- 路径：`GET /social/article/count/{articleId}`
- 作用：获取文章点赞数、评论数、收藏数、分享数、浏览数和当前用户是否点赞/收藏。

输出：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| articleId | Long | 文章 ID |
| likeCount | Long | 点赞数 |
| commentCount | Long | 评论数 |
| favoriteCount | Long | 收藏数 |
| shareCount | Long | 分享次数 |
| viewCount | Long | 浏览数 |
| liked | Boolean | 当前用户是否点赞 |
| favorited | Boolean | 当前用户是否收藏 |

### 批量获取文章统计

- 路径：`GET /social/article/counts?articleIds=<publicId1>&articleIds=<publicId2>`
- 作用：内容列表批量补充社交统计（含 `favoriteCount`、`shareCount`、`favorited`）。

### 浏览历史

- 路径：`GET /social/browse/history`
- 作用：分页查询当前用户浏览历史。
- 登录：需要

### 点赞文章列表

- 路径：`GET /social/like/article/list`
- 作用：分页查询当前用户点赞过的文章。
- 登录：需要

## 收藏接口

### 文章收藏 / 取消收藏

- 收藏：`POST /social/favorite/article/{articleId}`
- 取消：`DELETE /social/favorite/article/{articleId}`
- 作用：收藏或取消收藏文章。
- 登录：需要
- 规则：与点赞相同，与作者互黑则拒绝；仅已发布文章可收藏。
- 并发策略：`t_social_favorite` 使用 `(user_id, article_id)` 唯一索引保证幂等，`favorite_count` 原子增减（`GREATEST` 保底 ≥0）。

### 收藏状态检查

- 路径：`GET /social/favorite/article/check/{articleId}`
- 登录：可选
- 输出：`data = true/false`

### 收藏文章列表

- 路径：`GET /social/favorite/article/list`
- 作用：分页查询当前用户收藏过的文章（个人页「收藏」Tab）。
- 登录：需要

## 分享计数接口

### 记录分享

- 路径：`POST /social/share/article/{articleId}`
- 作用：用户主动分享时累计 `share_count`（复制链接 / 站内转发 / 外部分享）。
- 登录：需要
- 请求体（可选）：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| channel | String | 否 | `link` / `repost` / `external` |

- 输出：`data = null`

## OG 分享落地页（content-service）

- 路径：`GET /share/post/{articleId}`
- 作用：返回带 `og:title` / `og:description` / `og:image` / `og:url` 的 HTML，并跳转到前端 `/post/{id}`。
- 登录：不需要（网关白名单）
- 配置：`share.frontend-base-url`（环境变量 `SHARE_FRONTEND_BASE_URL`）

### 关注 Feed 信箱

- 路径：`GET /social/feed`
- 作用：查询当前用户关注流。优先读取 `t_social_feed_item` 信箱；信箱不足时，根据游标时间回源查询已关注作者的更早文章并补偿写入信箱。
- 登录：需要

请求参数：

| 字段 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| before | String | 否 | 当前时间 | 游标时间，格式为 `yyyy-MM-ddTHH:mm:ss` |
| beforeArticleId | Long | 否 | - | 与 `before` 配套的同时间游标 ID，避免同一时间戳文章分页重复或遗漏 |
| size | Long | 否 | 20 | 每次拉取数量，最大 100 |

输出 `data[]`：内容为文章骨架 `Article`，包含 `id`、`userId`、`title`、`summary`、`coverUrl`、`categoryId`、`publishedTime` 等字段。

### 发布文章推送到粉丝信箱（服务间调用）

- 路径：`POST /feign/social/feed/publish`
- 作用：content-service 在文章审核通过并发布后调用，将文章基础信息推入作者粉丝的 Feed 信箱。
- 登录：不使用用户登录态，必须携带 `X-Internal-Token`，两端使用同一 `SOCIAL_INTERNAL_TOKEN`。

请求参数：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| authorId | Long | 是 | 作者用户 ID |
| articleId | Long | 是 | 文章 ID |
| publishedTime | String | 是 | 发布时间，格式为 `yyyy-MM-ddTHH:mm:ss` |

## 关注与黑名单接口

### 关注 / 取关

- 关注：`POST /social/follow/{targetUserId}`
- 取关：`DELETE /social/follow/{targetUserId}`
- 登录：需要
- 并发策略：`t_social_follow` 使用 `(user_id, follow_user_id)` 唯一索引保证重复关注幂等。
- Feed 补偿：首次关注成功后，会拉取被关注用户最近发布的文章补偿写入当前用户信箱。

### 拉黑 / 取消拉黑

- 拉黑：`POST /social/follow/black/{targetUserId}`
- 取消：`DELETE /social/follow/black/{targetUserId}`
- 登录：需要
- 规则：拉黑时会删除双方关注关系，避免黑名单内仍互相关注；拉黑后双方不能关注、评论、回复、点赞对方内容。

### 关注列表

- 路径：`GET /social/follow/list`
- 作用：分页查询关注列表。
- 参数：`userId` 可选，默认当前用户；`page` 默认 1；`size` 默认 20。

### 粉丝列表

- 路径：`GET /social/follow/fans`
- 参数同关注列表。

### 黑名单列表

- 路径：`GET /social/follow/black/list`
- 作用：分页查询当前用户黑名单。

### 关系检查与统计

- 是否关注：`GET /social/follow/check/{targetUserId}`
- 是否拉黑：`GET /social/follow/black/check/{targetUserId}`
- 数量统计：`GET /social/follow/count/{userId}`

统计输出：

```json
{
  "following": 3,
  "fans": 8
}
```

## 举报接口

### 创建举报

- 路径：`POST /report`
- 作用：举报文章、评论、回复或用户。
- 登录：需要

请求参数：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| targetType | Integer | 是 | 1-文章，2-评论，3-回复，4-用户 |
| targetId | String | 是 | 目标标识：文章传 publicId，用户传 accountId，评论/回复传数字 ID |
| reason | String | 是 | 举报原因 |

输出：`data = 举报ID`

### 审核中心工单分页

- 路径：`GET /audit/moderation/page`
- 登录：管理员
- 作用：统一查看举报、帖子审核、资料审核工单。
- 参数：`page`、`size`、`status`、`taskType`

### 审核中心工单详情

- 路径：`GET /audit/moderation/{taskId}`
- 登录：管理员
- 作用：点开工单时按需通过 Feign 拉取目标详情，并校验目标状态和更新时间快照。

### 审核中心认领与处理工单

- 路径：`POST /audit/moderation/{taskId}/claim`，随后 `PUT /audit/moderation/{taskId}`
- 登录：管理员
- 作用：先通过租约令牌认领，再提交处理；同一工单只允许一个有效认领者，租约过期后自动重新入队。

请求参数：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| handleAction | String | 是 | 处理动作，如 `NO_VIOLATION`、`OFFLINE_ARTICLE`、`AUDIT_APPROVE` |
| claimToken | String | 是 | 认领接口返回的租约令牌 |
| requestId | String | 是 | 前端生成的幂等请求号 |
| handleRemark | String | 否 | 处理说明 |
