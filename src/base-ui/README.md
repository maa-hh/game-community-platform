# `base-ui/` 基础 UI 组件说明

本目录存放**无业务语义**的通用 UI 组件，可被任意页面/业务组件复用。

差异只由 props / 类型决定，不写业务分支（登录、关注、发帖等）。

---

## 组件清单

| 组件            | 用途                                                   |
| --------------- | ------------------------------------------------------ |
| `SurfaceCard`   | 白底圆角内容盒（详情卡 / 编辑器外壳）                  |
| `StatAction`    | 统一互动按钮（like/favorite/share/comment/view/reply） |
| `UserAvatar`    | 头像；无图用昵称首字                                   |
| `MediaCover`    | 媒体封面（可选视频播放标识）                           |
| `PageLoading`   | 居中 Spin 加载                                         |
| `ContentCard`   | 信息流帖子卡（强制，见 `ContentCard/README.md`）       |
| `CoverGallery`  | 横向滑动多图                                           |
| `ImageLightbox` | 大图预览框                                             |
| `ClampText`     | 两行截断 +「全文」                                     |
| `VideoPlayer`   | DPlayer 16:9                                           |
| `InfoCard`      | 演示用信息卡                                           |

### 工具

- `@/utils/formatCount`
- `@/utils/postType`（亦可从 `@/base-ui/ContentCard/types` 兼容导出）

---

## 与 `components/` 的边界

| 放 base-ui       | 放 components                    |
| ---------------- | -------------------------------- |
| 无业务语义       | 有业务语义（关注、评论区、分享） |
| 只接收通用 props | 可能读 store、调 service         |
| 任意场景可复用   | 本产品域专用                     |

---

## 相关

- `src/components/README.md`
- `PROJECT_STRUCTURE.md`
- `DESIGN.md`
