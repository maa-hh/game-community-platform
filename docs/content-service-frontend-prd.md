# Content Service Frontend PRD

## 1. 目标

前端内容模块需要把当前“模块占位页”升级为真实可用的内容广场与创作流，覆盖：

- 内容广场列表
- 文章详情页
- 发帖 / 编辑器
- 我的文章
- 分类筛选
- 内容图片上传
- 文章发布状态反馈

## 2. 页面范围

### 2.1 内容广场页

路径建议：
- `/app/content`

功能：
- 顶部展示分类筛选
- 列表展示已发布文章
- 支持“最新”刷新
- 支持“加载更多”

列表卡片字段：
- 封面图
- 标题
- 摘要
- 分类名
- 发布时间
- 作者信息占位

接口：
- `GET /category/listEnabled`
- `GET /article/latest`
- `GET /article/more`
- 或统一使用 `GET /article/page`

### 2.2 文章详情页

路径建议：
- `/app/content/article/:id`

功能：
- 展示标题、封面、摘要、正文、正文图片
- 展示审核状态信息时，仅对作者本人可见

接口：
- `GET /article/{id}`
- `GET /article/{id}/content`

### 2.3 发帖 / 编辑页

路径建议：
- `/app/content/editor`
- `/app/content/editor/:id`

功能：
- 标题输入
- 摘要输入
- 分类选择
- 正文编辑
- 正文图片上传
- 封面图选择
- 保存草稿
- 立即发布
- 定时发布

接口：
- `POST /file/upload`
- `POST /article`
- `PUT /article/{id}`

交互要求：
- 草稿保存成功要即时提示
- 发布后根据返回状态提示“已提交审核”或“已保存”
- 如果文章被驳回，编辑页进入时展示 `auditMessage`

### 2.4 我的文章页

路径建议：
- `/app/content/my`

功能：
- 按状态分组查看我的文章
- 支持继续编辑草稿
- 支持查看驳回原因
- 支持删除文章

接口：
- `GET /article/my`
- `DELETE /article/{id}`

显示状态：
- 草稿
- 审核中
- 已发布
- 已下架
- 已驳回

### 2.5 管理后台内容页

路径建议：
- `/app/admin/content`

功能：
- 关键词、分类、状态、作者筛选
- 管理员下架 / 删除文章

接口：
- `GET /article/admin/page`
- `PUT /article/admin/{articleId}/status`
- `DELETE /article/admin/{articleId}`
- `GET /article/admin/count`

### 2.6 分类管理页

路径建议：
- `/app/admin/content/categories`

功能：
- 分类列表
- 新建分类
- 编辑分类
- 启用 / 禁用
- 删除分类

接口：
- `GET /category/all`
- `POST /category`
- `PUT /category`
- `DELETE /category/{id}`

## 3. 前端数据模型建议

### 3.1 编辑器本地状态

建议维护：
- `id`
- `title`
- `summary`
- `content`
- `coverUrl`
- `imageUrls`
- `categoryId`
- `status`
- `scheduledPublishTime`

### 3.2 状态映射

后端 `status` 建议映射为：
- `0` 草稿
- `1` 已发布
- `2` 审核中
- `3` 已下架
- `4` 已驳回

当 `status=4` 时，优先展示：
- `auditMessage`

## 4. 关键交互要求

### 4.1 上传

- 图片上传后立即返回公网 URL
- 前端先本地预览，再把 URL 写入编辑器状态
- 删除图片时仅删除编辑器引用，不自动回收远端文件

说明：
- 当前后端会在审核驳回时主动清理本次发布使用的图片

### 4.2 发布按钮

按钮分三类：
- 保存草稿
- 立即发布
- 定时发布

校验：
- 标题必填
- 正文必填
- 分类必填

### 4.3 审核反馈

当后端返回文章详情时：
- `status=2` 展示“审核中”
- `status=4` 展示 `auditMessage`
- `status=1` 展示“已发布”

### 4.4 列表体验

内容广场页建议：
- 首屏用 `latest`
- 后续滚动用 `more`
- 分类切换时重置列表

## 5. API 对接表

### 5.1 创作流

- 上传图片：`POST /file/upload`
- 新建：`POST /article`
- 编辑：`PUT /article/{id}`
- 删除：`DELETE /article/{id}`

### 5.2 浏览流

- 分类列表：`GET /category/listEnabled`
- 广场分页：`GET /article/page`
- 最新内容：`GET /article/latest`
- 更多内容：`GET /article/more`
- 文章详情：`GET /article/{id}`
- 文章正文：`GET /article/{id}/content`

### 5.3 个人中心

- 我的文章：`GET /article/my`

### 5.4 管理后台

- 文章分页：`GET /article/admin/page`
- 文章计数：`GET /article/admin/count`
- 改状态：`PUT /article/admin/{articleId}/status`
- 删除文章：`DELETE /article/admin/{articleId}`
- 分类列表：`GET /category/all`
- 新建分类：`POST /category`
- 编辑分类：`PUT /category`
- 删除分类：`DELETE /category/{id}`

## 6. 前端实现建议

### 6.1 路由拆分

建议新增页面组件：
- `ContentFeedPage`
- `ArticleDetailPage`
- `ArticleEditorPage`
- `MyArticlesPage`
- `AdminContentPage`
- `AdminCategoryPage`

### 6.2 API 文件拆分

建议新增：
- `frontend/src/api/content.ts`

按领域拆成：
- `articleApi`
- `categoryApi`
- `contentFileApi`

### 6.3 状态管理

编辑页建议处理三种本地状态：
- `idle`
- `saving`
- `publishing`

并区分服务端返回：
- 成功
- 审核中
- 驳回

## 7. 后续前端增强项

后续可以继续做：

1. 富文本编辑器
2. 草稿自动保存
3. 文章卡片骨架屏
4. 驳回原因高亮展示
5. 创作者主页内容列表
