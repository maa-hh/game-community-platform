# content-service 附录规范

> **通用规范（必读）**：[`../CODING_STANDARDS.md`](../CODING_STANDARDS.md)

产品设计：`docs/v2/content-service.md`

---

## 1. API 前缀

| 类型 | 路径 |
|------|------|
| 文章 | `/article/**` |
| 分类 | `/category/**` |
| 文件 | `/file/**` |
| 分享页 | `/share/post/**` |
| Feign | `/feign/content/**` |

## 2. 包结构

```
com.game.community.content
├── controller/
├── feign/
├── service/ + impl/
├── mapper/
├── common/converter/    # ArticleConverter、CategoryConverter
├── mongo/
├── config/、filter/、aspect/、event/、schedule/
```

## 3. HTTP 分层

- Controller **只转发** `ArticleService` / `CategoryService` / `FileUploadService` / `SharePageService` 的 `Result` / `PageResult`
- 取当前用户 ID 在 Service 内（`UserThreadLocal`），不在 Controller
- 对外列表/详情用 `ArticleListVO`、`ArticleDetailVO`、`ArticleContentVO`、`CategoryVO`
- 分类写操作用 `CategoryDTO`，禁止 `@RequestBody Category`

## 4. 字符集

- 分类名、文章标题等中文展示字段：遵循通用规范 [`§7.1`](../CODING_STANDARDS.md#71-字符集与中文乱码强制)
- 种子脚本：`sql/content-category-seed.sql`（`SET NAMES utf8mb4` + 按 `sort` 可重入）

## 5. Service 职责

| Service | 职责 |
|---------|------|
| `ArticleService` | 文章 CRUD、审核、列表；`*Api` / `query*` 方法供 HTTP |
| `CategoryService` | 分类；`*Api` 方法返回 VO |
| `FileUploadService` | 图片直传 + 分片上传门面 |
| `SharePageService` | OG 分享 HTML |
| `ArticleContentService` | Mongo 正文 |
| `ChunkUploadService` | 分片会话（内部） |
| `TaskService` | 审核发布任务 |

## 6. 状态与常量

业务状态优先 `ContentConstants.ArticleStatus` 等；新类型宜补 `model.enums`（逐步迁移）。

## 7. 测试

```bash
mvn -pl service/content-service -am test
```
