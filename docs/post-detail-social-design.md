# 帖子详情 · 互动 · 分享设计

> 状态：**P0 已完成（前端 mock + 后端 API）**；下一阶段 P1 联调  
> 产品参考：[小黑盒帖子页](https://xiaoheihe.cn/app/bbs/link/184600162)  
> 关联：`docs/content-posting-design.md`（发帖三模式）、`docs/social-service-api.md`（现有互动）、前端 `DESIGN.md` §5.1（列表 ContentCard）

### 落地进度

| 项 | 状态 |
|----|------|
| 设计文档（转发=发帖 REPOST） | ✅ |
| P0 前端：详情/最新列表 mock/视频小窗/评论/分享 | ✅ |
| P0 后端：收藏/分享计数/REPOST/OG | ✅ `sql/post-detail-social-alter.sql` |
| P1 前后端联调 + 个人页收藏 Tab | ⏳ |

---

## 0. 已确认范围

| 项 | 决定 |
|----|------|
| 页面 | `/post/:id` 详情页；首页「最新帖」列表（ContentCard）可点进详情 |
| 数据 | 先 mock 可跑通 UI，再切真接口（可开关） |
| 未登录 | 可看详情与评论；点赞 / 评论 / 回复 / 举报 / 收藏 / 关注 / 拉黑 / 分享写操作 → 登录弹窗 |
| 评论 | 小黑盒式两级：评论 + 二级回复（再回复仍挂评论下，带 `@昵称`）；评论/回复正文 **超过 2 行省略**，可展开；**回复默认只展示 2 条**，点「展开全部」看全部 |
| 收藏 | **后端实现** + 详情按钮 + **个人页「收藏」Tab 本期接真** |
| 关注 / 拉黑 | 详情作者区做；拉黑后禁互动（沿用 social 现有规则） |
| 删除 | 可删自己的评论 / 回复（已有 DELETE API） |
| 视频帖 | 点列表卡进详情即视频布局：小窗常驻（边看评论边播），支持全屏、静音 |
| 分享 | **A 复制链接 + 计数**；**B1 站内转发卡片**；**B2 外链 Open Graph 预览卡** |

本期不做：热门评论排序算法深化、管理端审举报 UI、海报图分享（B3）。

### 转发动态 = 发帖的一种（2026-07-24 补充）

站内转发（B1）**不是**独立社交表主路径，而是 **content 发帖类型**：

| `postType` | 含义 |
|------------|------|
| 1 图文 / 2 文章 / 3 视频 | 已有 |
| **4 转发 `REPOST`** | 引用原帖 + **个人评论（附言）**；本身是帖，可赞/评/藏 |

- 分享面板「转发动态」→ `POST /article`（`postType=4`，`refArticleId`，`content`=个人评论，可走轻量审核或直发策略另定）
- 列表用 ContentCard（标签「转发」+ 附言摘要）；详情展示附言 + 原帖 `ShareCard`（点击进原帖）
- 成功转发后同时 `share_count+1`（channel=`repost`）
- 关注 Feed 推送与普通发帖同一套 `feed/publish`

---

## 1. 信息架构

### 1.1 首页「最新帖」

- 路由：`/`（或首页区块）
- 列表：**必须** `base-ui/ContentCard`（`DESIGN.md` §5.1）
- 数据：mock `latest` → 后接 `GET /article/latest`（或 `more`）+ `GET /social/article/counts`
- 点击整卡（含视频封面）→ `navigate(/post/:id)`，**不再**在首页浮窗播视频

### 1.2 详情页 `/post/:id`

```text
┌─ 作者行：头像 · 昵称 · 时间 · [关注] · [⋯更多：拉黑/举报帖子] ─┐
│ 标题                                                         │
│ 分区标签                                                     │
│ 【图文/文章】正文区                                          │
│ 【视频】吸顶/粘性播放器小窗（可拖、静音、全屏）+ 下方介绍文案   │
│ 统计：浏览 · 赞 · 评 · 藏 · 分享                             │
│ 操作：赞 · 收藏 · 评论(锚点) · 分享 · 举报                   │
├─ 评论区 ─────────────────────────────────────────────────────┤
│ 评论（2 行省略） 赞 · 回复 · 举报 ·（自己可删）               │
│   └ 回复 @某人（2 行省略） 赞 · 回复 · 举报 ·（自己可删）     │
│ 底栏输入：发评论 / 回复中提示「回复 @xx」                     │
└──────────────────────────────────────────────────────────────┘
```

详情页**不用** ContentCard；列表与详情职责分离。

### 1.3 视频帖交互

| 行为 | 说明 |
|------|------|
| 进入详情 | 自动起播（可记忆用户静音偏好） |
| 小窗 | 页面滚动时播放器保持可见（sticky / floating 模式复用 `VideoPlayer`） |
| 能力 | 播放/暂停、进度、±5s、倍速、**静音**、**全屏**、关闭小窗仅暂停不卸载路由 |
| 列表 | 只展示封面+播放标识，点击进详情再播 |

---

## 2. 互动矩阵

| 动作 | 帖子 | 评论 | 回复 | 登录 |
|------|------|------|------|------|
| 点赞/取消 | ✅ | ✅ | ✅ | 需要 |
| 收藏/取消 | ✅ | — | — | 需要 |
| 评论 | ✅ | — | — | 需要 |
| 回复 | — | ✅ | ✅（仍挂原评论，`replyToUserId`） | 需要 |
| 删除（仅作者本人） | — | ✅ | ✅ | 需要 |
| 举报 | ✅ | ✅ | ✅ | 需要 |
| 分享 A/B1/B2 | ✅ | — | — | 写操作需要；复制链接未登录可只复制不计数 |
| 关注 / 取关作者 | ✅ | — | — | 需要 |
| 拉黑作者 | ✅ | — | — | 需要 |

拉黑后：后端已拒赞/评/回；前端禁用对应按钮并 Toast。

---

## 3. 后端设计（social-service 为主）

### 3.1 已有（直接复用）

- 评论 / 回复 CRUD、点赞、浏览历史、文章统计、关注、黑名单、举报、关注 Feed  
- 见 `docs/social-service-api.md`

### 3.2 统计表扩展 `t_social_article_stats`

新增列：

```sql
favorite_count BIGINT NOT NULL DEFAULT 0 COMMENT '收藏数',
share_count    BIGINT NOT NULL DEFAULT 0 COMMENT '分享次数（A/B1/B2 写操作累计）'
```

`ArticleStatsVO` / `counts` 批量接口同步返回：`favoriteCount`、`shareCount`、`favorited`（当前用户是否已藏）。

### 3.3 收藏

**表 `t_social_favorite`**

```sql
CREATE TABLE IF NOT EXISTS t_social_favorite (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    article_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_social_favorite_user_article (user_id, article_id),
    KEY idx_social_favorite_user_time (user_id, create_time, id)
) COMMENT='用户收藏文章';
```

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/social/favorite/article/{articleId}` | 收藏；幂等；`favorite_count+1` |
| DELETE | `/social/favorite/article/{articleId}` | 取消；`favorite_count-1`（≥0） |
| GET | `/social/favorite/article/check/{articleId}` | 是否已藏 |
| GET | `/social/favorite/article/list` | 我的收藏分页（个人页 Tab） |

规则：与点赞相同，与作者互黑则拒绝；仅已发布文章可藏（Feign 校验 content 状态，或信任调用方 + 定时校验）。

### 3.4 分享计数（形态 A 共用计数出口）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/social/share/article/{articleId}` | body: `{ channel: 'link' \| 'repost' \| 'external' }`；`share_count+1` |

- 未登录：前端只复制链接，**不调**此接口  
- 登录后：复制链接成功 / 站内转发成功 / 调起外部分享成功 → 各记一次（可对 `user_id+article_id+channel+日期` 做轻度防刷，首期可先按次累加）

### 3.5 站内转发（B1）= 发帖类型 REPOST

**定案**：转发动态视为发帖，`postType = 4`，可带个人评论（附言）。

**content-service**

| 字段 | 说明 |
|------|------|
| `post_type=4` | REPOST |
| `ref_article_id` | 原帖 ID（必填） |
| `content` / summary | 转发者个人评论（可空或最少 0 字，产品可要求非空） |
| `cover_url` | 可冗余原帖封面便于列表 |

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/article` | `postType=4` + `refArticleId` + `content`（附言）；分类可沿用原帖或固定「转发」分区 |
| GET | `/article/{id}` | 详情带 `refArticle` 骨架（Feign/联表），供 ShareCard |

**social-service**：转发成功后前端或 content 回调 `POST /social/share/article/{原帖id}` channel=`repost`。  
不再依赖独立 `t_social_repost` 作为主实体（若已草案可废弃）。

**前端**：`ShareSheet` 附言输入 → 发帖 API；Feed/详情用 `components/ShareCard` 展示原帖引用盒。

### 3.6 外链预览卡（B2 · Open Graph）

SPA 爬虫拿不到 React 内容，需 **服务端返回带 meta 的 HTML**。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/share/post/{articleId}` | 返回 `text/html`：`og:title` / `og:description` / `og:image` / `og:url`；`<meta http-equiv="refresh">` 或链接跳到前端 `/post/{id}` |

部署建议：

- 网关将 `/share/post/**` 指到 content-service 或独立 share 模块（需 Feign 拉标题/摘要/封面）  
- 前端「复制链接」默认复制 **短链或 OG 落地链**：`https://域名/share/post/{id}`（进入后跳详情）；也可用前端路由 `/post/{id}` 并在网关对微信 UA 做 OG 分流（二选一，**推荐独立 `/share/post/{id}`**，实现简单）

微信等平台卡片样式不可控；我们只保证标题/封面/摘要正确。

### 3.7 游客可读

| 接口 | 游客 |
|------|------|
| `GET /article/{id}`、`/content`、`latest` | 需网关白名单（仅已发布） |
| `GET /social/comment/list`、`reply/list`、`article/count` | 可选登录（已有） |
| `GET /social/article/{id}` 记浏览 | 登录才记历史；游客可不调或另做匿名浏览（首期游客不记 history） |

---

## 4. 前端设计

### 4.1 路由与目录

| 路由 | 页面 |
|------|------|
| `/` | Home：最新帖 ContentCard 列表 |
| `/post/:id` | `views/PostDetail/` |
| `/post/editor` | 已有 |

```
views/PostDetail/
  index.tsx              # 拼装
  components/
    PostAuthorBar/       # 关注/更多
    PostBody/            # 三模式正文
    PostActionBar/       # 赞藏评分享举报
    CommentSection/      # 列表+输入
    CommentItem/         # 两行省略、操作
  style.less
components/ShareCard/    # B1 引用盒
components/ShareSheet/   # 分享面板：复制链接 / 站内转发 / 系统分享
service/social.ts        # 新建
service/content.ts       # 补详情/最新列表
mock/                    # post detail + social
```

### 4.2 登录拦截

统一：`useAuthModal` / 现有 AuthModal；写操作前 `if (!user) openAuth('login')`。

### 4.3 评论 UI

- 一级评论：正文 CSS `-webkit-line-clamp: 2`，超出显示「全文」
- 二级回复：缩进；展示 `回复 @昵称`；同样 2 行省略
- 「回复」把 `replyToUserId` / 昵称写入输入框状态

### 4.4 Mock 开关

`REACT_APP_USE_MOCK_SOCIAL=true`（或沿用现有 mock 拦截）：详情、评论树、赞藏状态本地可写；开关关闭走真 API。

### 4.5 个人页

| Tab | 数据 |
|-----|------|
| 收藏 | `GET /social/favorite/article/list` + 批量文章骨架 + ContentCard |
| 赞过 | 已有 `like/article/list`（本期可一并接真） |
| 历史 | `browse/history` |
| 转发（可选） | `repost/my` + ShareCard |

---

## 5. 分享三形态对照（实现口径）

| 形态 | 用户动作 | 前端 | 后端 | 计数 |
|------|----------|------|------|------|
| **A 链接** | 复制链接 / 系统分享 | Clipboard / `navigator.share` | `POST /social/share` channel=`link`；链接指向 `/share/post/{id}` 或 `/post/{id}` | ✅ |
| **B1 站内卡** | 转发动态（发帖） | ShareSheet 填个人评论 → `postType=4` | `POST /article` + share 计数 | ✅ |
| **B2 外链卡** | 把链接贴到微信等 | 复制的是 OG 落地 URL | `GET /share/post/{id}` HTML meta | 用户主动点「分享」时 ✅；仅粘贴不一定回传，以 A 的点击为准 |

---

## 6. 实现分期

| 阶段 | 内容 | 产出 |
|------|------|------|
| **D0** | 本文档评审通过 | ✅ 本文 |
| **P0 前端骨架** | 路由详情页、首页最新列表 mock、视频常驻小窗、评论两级+两行省略、未登录拦截、ShareSheet UI | 可点击演示 |
| **P0 后端** | favorite 表/API、stats 增列、share 计数、repost 表/API、Feed 类型、OG HTML | SQL + 服务 |
| **P1 联调** | content 读接口 + social 全联动；个人页收藏 Tab 接真；网关游客可读 | ✅ |
| **P1 完善** | 关注/拉黑/删评回/举报；B1 Feed 展示 ShareCard；B2 网关路由 | 功能闭环 |
| **P2** | 防刷、游客浏览策略、转发 Tab 体验打磨 | 增强 |

---

## 7. 验收清单（摘要）

1. 首页最新帖 ContentCard → 进详情  
2. 图文/文章/视频三详情形态正确；视频可边看评论边播、静音、全屏  
3. 游客可读；写操作弹登录  
4. 赞/藏/评/回/删自己/举报可用  
5. 关注/拉黑可用且拉黑后禁互动  
6. 分享：复制链接成功；站内转发出卡片且可点回原帖；外链 OG 在调试工具可见 title/image  
7. 个人页「收藏」为真实列表  
8. `lint` + `typecheck`；后端编译通过  

---

## 8. 相关路径

| 端 | 路径 |
|----|------|
| 本设计 | `docs/post-detail-social-design.md` |
| 发帖 | `docs/content-posting-design.md` |
| Social API | `docs/social-service-api.md` |
| 前端设计 | `game-community/DESIGN.md` |
| 前端播放器 | `game-community/docs/dplayer-capability.md` |
| ContentCard | `game-community/src/base-ui/ContentCard/README.md` |
