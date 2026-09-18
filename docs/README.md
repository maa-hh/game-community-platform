# 前端技术文档目录

本文档集以当前 `src/` 代码、`package.json`、CRACO 配置和后端同远程仓库中的 `docs/` 为事实来源。文档描述实现方式、数据边界、失败处理和维护规则，不把早期方案或已经删除的组件继续当作现状。

## 文档地图

| 文档                                                   | 适读对象       | 内容                                                       |
| ------------------------------------------------------ | -------------- | ---------------------------------------------------------- |
| [`frontend-architecture.md`](frontend-architecture.md) | 全体开发者     | 启动链路、布局、路由、状态、请求、主题、部署和性能         |
| [`frontend-modules.md`](frontend-modules.md)           | 功能开发者     | service、store、hooks、views、mock、utils 的职责与调用关系 |
| [`frontend-components.md`](frontend-components.md)     | UI/组件开发者  | base-ui、业务组件、页面子组件的 API 责任和组合边界         |
| [`frontend-contracts.md`](frontend-contracts.md)       | 前后端联调者   | API 前缀、响应、鉴权刷新、上传、SSE、分页和兼容检查        |
| [`post-detail-design.md`](post-detail-design.md)       | 帖子功能开发者 | 前端帖子详情交互约束                                       |
| [`social-feed-design.md`](social-feed-design.md)       | 信息流开发者   | 信息流布局与缓存设计                                       |
| [`dplayer-capability.md`](dplayer-capability.md)       | 视频功能开发者 | DPlayer 能力和封装边界                                     |

## 代码旁文档

- [`../PROJECT_STRUCTURE.md`](../PROJECT_STRUCTURE.md)：目录归属、布局与数据流总规范。
- [`../DESIGN.md`](../DESIGN.md)：颜色 token、宽度、卡片、顶栏和交互规范。
- [`../src/components/COMPONENT_STRUCTURE.md`](../src/components/COMPONENT_STRUCTURE.md)：业务组件拆分规则。
- [`../src/base-ui/README.md`](../src/base-ui/README.md)：基础 UI 组件目录和复用边界。
- [`../src/components/README.md`](../src/components/README.md)：业务组件目录和使用原则。
- [`../src/router/README.md`](../src/router/README.md)：路由与鉴权守卫。
- [`../src/service/README.md`](../src/service/README.md)：HTTP、上传、SSE 与错误处理。
- [`../src/store/README.md`](../src/store/README.md)：Redux/RTK Query 缓存规则。
- [`../src/assets/css/README.md`](../src/assets/css/README.md)：全局样式、主题 token 和 reset。

## 后端对齐入口

前端代码与后端 `master` 分支使用以下文档对照：

| 领域                         | 后端文档                                                                                       |
| ---------------------------- | ---------------------------------------------------------------------------------------------- |
| 模块边界、端口和网关         | `docs/architecture/README.md`                                                                  |
| 账号、资料、装扮             | `docs/v2/user-service.md`                                                                      |
| 文章、审核、文件上传         | `docs/v2/content-service.md`、`docs/content-posting-design.md`                                 |
| 评论、回复、互动、关注、举报 | `docs/v2/social-service.md`、`docs/post-detail-social-design.md`                               |
| 通知与 SSE                   | `docs/v2/notification-service.md`                                                              |
| Steam、游戏和游戏关注        | `docs/v2/steam-service-api-inventory.md`                                                       |
| 热榜、搜索、商城             | `docs/v2/recommend-service-current.md`、`docs/v2/search-service.md`、`docs/v2/shop-service.md` |
| 审核                         | `docs/v2/audit-service.md`、`docs/v2/ai-agent-service.md`                                      |

同一接口的路径、HTTP 方法、请求字段、业务码或分页字段改变时，必须同时更新前端 `service/`、类型、页面状态转换、mock 和本目录相关文档，并在提交说明中写清兼容策略。

## 文档维护规则

1. 先读代码再改文档；组件已经删除时同时移除组件清单和示例。
2. 路由表只在 `src/router/routes.tsx` 维护；文档表格与该文件逐项一致。
3. API 路径以 `src/service/*.ts` 和后端 Controller/文档为准，不凭接口名称推断行为。
4. 文档不写真实密钥、账号、生产域名、个人路径、日志内容或数据库真实数据。
5. 所有中文文档以 UTF-8 保存；代码行为与文档冲突时先修正文档事实，再决定是否需要代码变更。
6. 合并前运行 `npm run lint`、`npm run typecheck`、`npm run build` 和 `git diff --check`。
