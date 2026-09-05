# `components/` 业务通用组件说明

本目录存放**带业务语义、跨页面复用**的组件。无业务语义的放 `base-ui/`。

**目录结构规范（必读）：** [`COMPONENT_STRUCTURE.md`](./COMPONENT_STRUCTURE.md) — 统一参照 `AppHeader/` 拆分；新组件必须遵守。

**原则**：能复用的一律抽取；页面差异只由**数据**与**类型分支**决定，不要复制一套 UI。

---

## 一、标准结构（AppHeader 模板）

```
ComponentName/
├── index.tsx       # 编排层
├── types.ts        # Props / 配置类型
├── config.ts       # 静态文案与选项（可选）
├── useXxx.ts       # 逻辑 Hook（可选）
├── parts/          # 子 UI 块（可选）
└── style.less
```

---

## 二、类型

| 文件                  | 内容                                                            |
| --------------------- | --------------------------------------------------------------- |
| `@/types/content`     | `Author`、`ContentCardData`、`ContentCardPostType`、`PostStats` |
| `@/types/post`        | 详情 / 评论 / 回复 / 转发卡 / 最新帖                            |
| `@/types/profile`     | 主页 Tab、FeedItem、ProfileStats                                |
| `@/utils/formatCount` | 统一数字展示                                                    |
| `@/utils/postType`    | `mapNumericPostType` / `resolvePostType`                        |

---

## 三、base-ui 原子 / 盒子

| 组件                                                           | 用途                                    |
| -------------------------------------------------------------- | --------------------------------------- |
| `SurfaceCard`                                                  | 统一白底圆角内容盒                      |
| `StatAction`                                                   | 赞 / 藏 / 分享 / 评论 / 浏览 / 回复按钮 |
| `UserAvatar`                                                   | 头像 + 首字 fallback                    |
| `MediaCover`                                                   | 封面图 / 视频封面+播放钮                |
| `PageLoading`                                                  | 统一加载态                              |
| `ContentCard`                                                  | 列表内容卡（强制）                      |
| `CoverGallery` / `ImageLightbox` / `ClampText` / `VideoPlayer` | 媒体与文本                              |

---

## 四、业务容器

| 组件                                                   | 结构       | 用途                 |
| ------------------------------------------------------ | ---------- | -------------------- |
| `AppHeader`                                            | L 标准模板 | 顶栏                 |
| `AuthModal`                                            | L          | 全局登录弹窗         |
| `CommentSection` / `CommentItem`                       | L / M      | 评论体系             |
| `ShareSheet` / `ShareCard`                             | L / M      | 分享与转发卡         |
| `FeedPanel` / `PostBottomBar`                          | M          | 信息流壳 / 底栏      |
| `ArticleProgressBanner`                                | M          | 审核进度条           |
| `AuthorHeader` / `AuthorScrollBanner` / `FollowButton` | S          | 作者行 / 吸顶 / 关注 |
| `PostActionBar` / `PostOwnerLinks`                     | S          | 详情互动 / 链接      |
| `CommentOps` / `CommentReply` / `ReplyPopup`           | S          | 评论操作             |
| `EmptyState` / `CounterPanel` / `RepostBlock`          | S          | 空状态等             |
| `auth/*`                                               | M          | 登录注册表单         |
| `profile/*`                                            | L / M      | 个人页相关           |

级别说明见 `COMPONENT_STRUCTURE.md` §二。

### Hook（`src/hooks/`）

| Hook                                               | 用途               |
| -------------------------------------------------- | ------------------ |
| `useRequireLogin`                                  | 未登录打开登录弹窗 |
| `useAuthModal` / `useTheme` / `useProfileAuditSse` | 全局 UI / SSE      |

---

## 五、用法示例

```tsx
import SurfaceCard from '@/base-ui/SurfaceCard';
import StatAction from '@/base-ui/StatAction';
import UserAvatar from '@/base-ui/UserAvatar';
import CommentSection from '@/components/CommentSection';
import { useRequireLogin } from '@/hooks/useRequireLogin';

const { requireLogin } = useRequireLogin();

<SurfaceCard>
  <UserAvatar name={author.nickname} src={author.avatar} size={40} />
  <StatAction kind="like" count={likeCount} active={liked} onClick={onLike} />
</SurfaceCard>;
```

---

## 六、相关文件

| 文件                     | 说明                           |
| ------------------------ | ------------------------------ |
| `COMPONENT_STRUCTURE.md` | **组件拆分规范（新组件必读）** |
| `src/base-ui/README.md`  | 无业务 UI                      |
| `PROJECT_STRUCTURE.md`   | 目录规范                       |
| `DESIGN.md`              | 视觉规范                       |
