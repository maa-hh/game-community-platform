# ContentCard — 帖子卡片（强制统一）

> **强制**：凡列表/信息流中的帖子类内容，统一用本组件展示。
> 设计依据：根目录 `DESIGN.md` §5.1。

## 用在哪里

| 场景                                  | 是否用 ContentCard              |
| ------------------------------------- | ------------------------------- |
| 首页 / 推荐 / 搜索「帖子」信息流      | ✅                              |
| 个人页：帖子 / 浏览历史 / 赞过 / 收藏 | ✅                              |
| 其它 Feed 列表里的帖子摘要卡          | ✅                              |
| 帖子详情全文页、发帖编辑器            | ❌（详情/编辑另做，不替代本卡） |

**禁止**在 `views/` 或 `components/` 里再抄一套「标题 + 三行正文 + 图 + 标签 + 赞评」布局。

## 帖子类型

| `postType`   | 含义                                   | 列表          | 详情                          |
| ------------ | -------------------------------------- | ------------- | ----------------------------- |
| `image_text` | **封面是图，正文全是文字**（小红书式） | 封面图条      | 封面横滑在上 → 纯文字正文     |
| `article`    | 文章，图来自正文插图                   | 正文抽图      | 富文本（可含图）              |
| `video`      | 视频                                   | 封面+播放钮   | 作者信息 + 16:9 播放器 + 介绍 |
| `repost`     | 转发动态                               | 原帖封面/附言 | 个人评论 + ShareCard          |

后端数值：`mapNumericPostType(1\|2\|3\|4)`。

## 固定结构

1. 作者（头像 + 昵称 + 时间）
2. 标题（最多两行省略）
3. 正文（最多三行省略）
4. 媒体区（按 `postType`）
5. 分区标签 + 评论 / 赞

## 用法

```tsx
import ContentCard from '@/base-ui/ContentCard';
import { mapNumericPostType } from '@/base-ui/ContentCard/types';

<ContentCard
  data={{
    id: String(item.id),
    author: { nickname: item.username, avatar: item.avatar },
    title: item.title,
    content: item.summary || '',
    postType: mapNumericPostType(item.postType),
    images: item.imageUrls, // 图文=封面；文章=正文图
    coverUrl: item.coverUrl, // 视频封面
    tags: [{ text: categoryName }],
    commentCount: item.commentCount,
    likeCount: item.likeCount,
    liked: item.liked,
    createdAt: item.createTime,
  }}
  onClick={() => navigate(`/post/${item.id}`)}
  onLikeClick={handleLike}
  onCommentClick={handleComment}
/>;
```

视频列表卡只展示封面；真正播放用 `base-ui/VideoPlayer`（见 `docs/dplayer-capability.md`）。

## 文件

| 文件             | 说明                                      |
| ---------------- | ----------------------------------------- |
| `index.tsx`      | 卡片主体                                  |
| `ImageStrip.tsx` | 图文/文章多图条 + 「共 N 张」             |
| `types.ts`       | `ContentCardData` / `postType` / 映射工具 |
| `style.less`     | 样式（Token：`--color-*`）                |
