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
- 分享：复制前端详情链接 `/post/:id`（A）+ 转发动态发帖 `postType=4`（B1）。后端 OG `/share/post/:id`（B2）只有部署层已配置反向代理时使用，不能作为默认复制地址。
- HTTP 页面或内嵌浏览器可能拒绝 Clipboard API：先尝试兼容复制；只有 `writeText` 成功或回读一致时才提示成功并关闭面板。旧 `execCommand` 结果不可验证，必须保留与普通正文相同的原生可选文本，禁止自动聚焦或程序全选，确保手机长按能弹出系统复制菜单。
- 转发以 `POST /article` 成功为完成边界；新帖处于待审核状态，不立即补查或跳转详情。分享计数后台补记，失败不反向误报发帖失败。
- 个人页「收藏」Tab：`GET /social/favorite/article/list`（已接真）

## 目录

```
src/views/PostDetail/
src/components/ShareCard/
src/components/ShareSheet/
src/service/social.ts
src/mock/posts.ts
```
