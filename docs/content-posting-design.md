# 发帖能力设计：可见性、分片上传与三模式编辑

> 依据现有 content-service（草稿→待审→异步审核→发布）扩展。  
> 大文件分片思路参考：[阿里云 · 大文件上传断点续传](https://developer.aliyun.com/article/1720673)。  
> 编辑器交互参考小黑盒图文/文章/视频创作台。

---

## 落地进度（2026-07-22）

| 项 | 状态 |
|----|------|
| 私有桶图片上传 + `pending://` | ✅ `POST /file/upload` |
| 视频分片 init/chunk/merge/abort/status | ✅ `/file/upload/*` |
| 详情非发布仅作者可见 | ✅ `getArticleDetail` / content |
| `PUT .../publish` 改为提交审核 | ✅ `submitForAudit` |
| 过审私有→公共 | ✅ `ArticleAsyncServiceImpl` |
| 删帖取消任务 + 清媒体 | ✅ |
| `postType` / `videoUrl` | ✅ 实体 + `sql/content-posting-alter.sql` |
| 前端三模式编辑器 | ✅ `/post/editor` |
| 上传/审核进度 API | ✅ `GET /article/{id}/progress` |
| 取消上架（停传+取消任务+改状态，留文件） | ✅ `PUT .../unpublish` |
| 删除（停传+取消任务+删文件） | ✅ `DELETE /article/{id}` |
| 前端进度展示 + 取消/删除 | ✅ 编辑器 + 个人页帖子 |

运维：已有库执行 `sql/content-posting-alter.sql`；content-service 单请求上限为 6MB，媒体默认按 3MiB/片并发上传。

---

## 1. 三种发帖模式

| 模式 | `postType` | 封面 | 正文 | 媒体 |
|------|------------|------|------|------|
| 图文 | `IMAGE_TEXT=1` | **封面图**（可多张） | **纯文字**（无正文插图，小红书式） | 仅封面 |
| 文章 | `ARTICLE=2` | 无独立封面 | 富文本，可插图/动图 | 正文内图片 |
| 视频 | `VIDEO=3` | 可选封面图 | 标题 + 视频介绍 | 主视频（分片上传） |
| 转发 | `REPOST=4` | 可继承原帖封面 | 个人评论（附言） | 引用原帖 |
公共字段：标题、分区 `categoryId`、状态（草稿/提交审核）。

---

## 2. 审核期如何保证「外人不可见」

必须 **双保险**：元数据状态门禁 + 媒体私有桶。

### 2.1 元数据（MySQL `t_article.status`）

| 状态 | 含义 | 广场/最新/Feed | 详情/正文 API |
|------|------|----------------|---------------|
| 0 草稿 | 仅作者 | ❌ | 仅作者 |
| 2 待审核 | 上传+审核中 | ❌ | 仅作者 |
| 4 驳回 | 仅作者可见原因 | ❌ | 仅作者 |
| 3 下架 | 作者/管理 | ❌ | 仅作者/管理 |
| 1 已发布 | 对外可见 | ✅ | ✅ |

**规则**：`GET /article/{id}`、`GET /article/{id}/content` 对非 `PUBLISHED` 必须校验当前用户是作者（或管理员），否则 404/无权限。  
禁止再用 `PUT /article/{id}/publish` 直接置 1 绕过审核；作者「提交上架」只能进入 `PENDING` 走任务队列。

### 2.2 媒体（MinIO）

| 阶段 | 桶 | 访问方式 |
|------|----|----------|
| 上传中 / 草稿 / 待审 | **私有桶** | 仅服务端可读；作者预览用短时 **Presigned URL** |
| 审核通过上架 | **复制到公有桶** | 过审时复制一次，数据库保存稳定公网 URL |
| 驳回 / 删除 | — | 删除对应桶对象与分片残留 |
| 取消上架 | **仍在公有桶** | 只改文章状态，并把媒体 URL 写入 Redis 黑名单 |

当前漏洞：图片直传公共桶，待审期 URL 可被猜中访问。改造后：

1. `POST /file/upload` → 私有桶，返回 `objectKey` + 临时 `previewUrl`  
2. 文章里存 `objectKey`（或 pending URL 协议，如 `pending://objectKey`）  
3. 审核通过：私有对象复制到公有桶，文章保存稳定公网 URL
4. 取消上架：不搬运大文件；文章作者/管理员可读，普通接口不再返回黑名单资源

### 2.3 进度对外暴露

作者侧轮询或 SSE：`uploadProgress`（分片）+ `auditStatus`（任务状态）。  
他人接口永不返回待审正文/媒体。

---

## 3. 媒体大小与格式限制（建议默认）

| 类型 | 限制 |
|------|------|
| 封面/插图 | ≤ 5MB；jpg/png/webp/gif；单帖图片总数 ≤ 20 |
| 视频 | ≤ 500MB；mp4/webm；分辨率建议 ≤ 1080p（可后置转码） |
| 分片大小 | 默认 5MB/片（与参考文一致，可配置） |

---

## 4. 大文件分片上传（视频）

对齐参考文前后端分工：

```
前端：文件 MD5 → 按片切分 → 并发/串行上传 → 展示进度
后端：会话登记 → 收片落私有桶 → 合并 → 返回 objectKey
取消：abort 会话 + 删分片前缀；删帖时一并清理
```

### 4.1 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/file/upload/init` | `{fileName,fileSize,fileMd5,contentType,bizType}` → `uploadId, chunkSize, totalChunks` |
| POST | `/file/upload/chunk` | `uploadId, chunkIndex, file` → 已收片数/进度 |
| POST | `/file/upload/merge` | `{uploadId}` → `{objectKey, previewUrl}` |
| POST | `/file/upload/abort` | `{uploadId}` → 清理残留 |
| GET | `/file/upload/{uploadId}/status` | 进度查询（可续传） |

秒传（可选）：init 时若全局已有相同 `fileMd5` 且属当前用户可用资源，直接返回已有 `objectKey`。

### 4.2 取消上架 / 删除

1. 取消上传：`abort` + 停前端队列  
2. 删除/撤回帖子：取消关联 `Task`（status=CANCELLED）+ 删私有媒体 + 删未合并分片  
3. 已发布下架：改 status=OFFLINE，可保留公共文件或异步回收  

---

## 5. 推荐状态机（作者视角）

```
编辑中(DRAFT)
  → 上传媒体(私有,可看进度)
  → 提交审核(PENDING, 任务入队)
  → 审核中(进度:文本/图片/视频)
  → 通过(PUBLISHED, 媒体复制到公有桶) | 驳回(REJECTED, 清私有可选保留草稿)
任意未发布阶段可「取消/删除」→ 取消只改状态并写黑名单；删除再清 MinIO 残留
```

---

## 6. 前端编辑器（对齐小黑盒）

- 路由建议：`/post/editor`（MainLayout 内）
- 顶栏模式切换：图文 / 文章 / 视频  
- 公共：标题、分区 Select、保存草稿 / 提交审核  
- 视频模式：分片上传进度条；取消按钮调 abort  
- 审核中页：「我的帖子」展示状态 Tag + 进度，不可被他人搜索到  

遵循根目录 `DESIGN.md`（内容盒、主色橙、卡片规范）。

前端视频播放器（DPlayer）能力与扩展索引：前端仓库 `docs/dplayer-capability.md`（[DIYgod/DPlayer](https://github.com/DIYgod/DPlayer)）。

帖子详情、评论互动、收藏与分享（A/B1/B2）：见同目录 `post-detail-social-design.md`。

---

## 7. 改造清单（相对现网）

| 项 | 现状 | 目标 |
|----|------|------|
| 图片上传 | 公共桶直传 | 私有桶 + 过审提升 |
| 详情可见性 | 任意人可读待审 | 非发布仅作者 |
| publish API | 直接 status=1 | 改为提交审核或删除该旁路 |
| 视频 | 无 | 分片会话 + 限制 |
| `postType` | 无 | 表字段 + DTO |
| 前端发帖 | Mock | 三模式编辑器 |

---

## 8. 与现有审核链衔接

沿用：`ArticleService.save` 非草稿 → `PENDING` + Redis 任务 → `ArticleAsyncService.auditAndPublish`。  
扩展：审核通过前完成私有→公共；驳回/取消清理私有与分片。视频审核可先做「元数据+抽帧」或首期仅校验格式大小、二期接 AI。
