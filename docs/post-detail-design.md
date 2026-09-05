# 帖子详情页（前端）

> 完整设计（含后端收藏 / 分享 A+B1+B2 / 分期）见后端仓：
> `game-community-platform/docs/post-detail-social-design.md`

## 进度

| 阶段                                     | 状态 |
| ---------------------------------------- | ---- |
| P0 mock 详情 + 首页最新帖 + 分享/评论 UI | ✅   |
| P1 接真 API + 个人页收藏                 | ✅   |

## 路由

| 路径           | 说明                      | 登录                   |
| -------------- | ------------------------- | ---------------------- |
| `/`            | 最新帖列表（ContentCard） | 游客可读               |
| `/post/:id`    | 详情：正文 + 评论 + 互动  | 游客可读；写操作弹登录 |
| `/post/editor` | 发帖                      | 需登录                 |

## 强制约定

- 列表帖子 → `ContentCard`（`DESIGN.md` §5.1）
- 未登录可看详情/评论；赞/评/回/举报/藏/关注/拉黑/转发写操作 → AuthModal
- 评论两级（回复带 `@`）；正文超过 2 行省略可展开
- 视频帖：详情内作者头像昵称在上，下方 `VideoPlayer` 16:9 正常流播放（可静音/全屏），不做 sticky 小窗
- 分享：复制链接（A）+ 转发动态发帖 `postType=4`（B1）+ OG `/share/post/:id`（B2）
- 个人页「收藏」Tab：`GET /social/favorite/article/list`（已接真）

## 目录

```
src/views/PostDetail/
src/components/ShareCard/
src/components/ShareSheet/
src/service/social.ts
src/mock/posts.ts
```
