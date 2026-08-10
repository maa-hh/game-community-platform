# 社交与内容能力对照（前端落地状态）

> 后端仓：`game-community-platform` · 网关默认 `http://127.0.0.1:8080`
> 前端联调：`.env.development` 设 `REACT_APP_ENABLE_MOCK=false`、`REACT_APP_BASE_URL=http://127.0.0.1:8080`

## 一、本次已落地（前端）

| 能力 | 接口 | 前端位置 |
|------|------|----------|
| 评论 / 回复 / 删评 / 删回 | `/social/comment/**`、`/social/reply/**` | `CommentSection`、`PostDetail` |
| 帖子 / 评论 / 回复点赞 | `/social/like/**` | 详情、评论区 |
| 收藏 | `/social/favorite/article/**` | `PostDetail` |
| 关注 / 取关 | `/social/follow/{id}` | `PostDetail`、`FollowButton` |
| **拉黑** | `POST/DELETE /social/follow/black/{id}` | `toggleBlockApi` → 帖子详情更多菜单 |
| **关注流（动态页）** | `GET /social/feed?postType&includeSelf` | `views/Feed` + 顶栏「动态」 |
| 举报 | `POST /report` | 评论 / 帖子 |
| 分享 / 转发 | `/social/share/**` + content 存稿 | `ShareSheet` |

### 动态页 `/feed`

- 顶栏 **动态**（需登录）
- 子 Tab：全部 / 图文 / 文章 / 视频 / 转发（对应 `postType` 1–4）
- 列表布局与首页一致：`PostFeedList` + `ContentCard` + `FeedPanel`
- 数据：关注的人 + **自己的已发布帖**（`includeSelf=true`，后端 `listFeed` 已支持）

### 后端 social-service 本次对齐

- `SocialServiceImpl#listFeed` 已实现接口签名五参数：`postType` 筛选、`includeSelf` 合并自己的帖、按发布时间排序

---

## 二、后端已有、前端未做（建议排期）

| 能力 | 接口 | 说明 |
|------|------|------|
| 关注流游标分页 | `GET /social/feed?before=` | 动态页目前首屏 20 条，待加「加载更多」 |
| 首页游标 | `GET /article/more` | 首页仍为 `/article/latest` 固定 20 条 |
| 浏览历史 | `GET /social/browse/history` | 可做「最近看过」Tab |
| 关注 / 粉丝列表 | `/social/follow/list`、`/fans` | 个人页统计与列表 |
| 关注数 / 粉丝数 | `/social/follow/count/{userId}` | 作者卡展示 |
| 拉黑列表 | `/social/follow/black/list` | 设置页管理黑名单 |
| 拉黑状态查询 | `/social/follow/black/check/{id}` | 详情页可预查是否已拉黑 |
| 他人主页帖列表 | `GET /article/author/{id}/published` | 点击作者进主页流 |
| 长文按需加载 | `GET /article/{id}/content` | 文章详情正文片段 |
| content 关注文章 | `GET /article/follow` | 与 social feed 二选一，优先 social feed |

---

## 三、content-service 建议保持现状

发帖、审核进度、分片上传、分类、发布/下架等已在 `service/content.ts` 封装；**不必**为 P1 再拆 social 职责。

---

## 四、前端样式约定（与首页一致）

- 页面壳：`views/Feed/style.less` — `padding: var(--card-stack-gap) 0 48px`（同 `home-page`）
- 类型 Tab：`antd Segmented` block，类名 `feed-page__tabs`
- 列表：**禁止**页面内手搓卡片，统一 `PostFeedList` → `ContentCard`
- 空态：`EmptyState` + 文案见 `views/Feed/config.ts` 的 `feedEmptyText`

---

## 五、自测清单

```bash
# 前端
npm run lint && npx tsc --noEmit

# 联调（需起 gateway + social + content + user）
REACT_APP_ENABLE_MOCK=false npm start
# 登录 → 顶栏「动态」→ 切换类型 Tab → 点帖进详情
# 详情 → 赞/藏/评/回/关注 → 更多 → 拉黑
```
