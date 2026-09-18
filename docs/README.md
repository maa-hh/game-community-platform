# 后端技术文档

本目录只保留与当前代码和部署方式相关的文档。旧的临时计划、生成 HTML、一次性架构快照和已移除功能的设计稿不再作为事实来源。

## 文档分层

| 文档 | 用途 |
|---|---|
| [`architecture/README.md`](architecture/README.md) | 模块边界、服务端口、依赖关系、请求链路和部署拓扑 |
| [`v2/<service>.md`](v2/) | 各服务当前实现、API、数据表、配置和测试说明 |
| `*-design.md` | 仍在实现或需要跨前后端协作的领域设计 |
| `*-prd.md` | 产品约束；实现不一致时以代码和 `docs/v2` 为准 |
| `service/../CODING_STANDARDS.md` | 后端代码、DTO/VO、SQL 和服务间协议规范 |

## 当前服务文档

| 服务 | 文档 |
|---|---|
| user | [`v2/user-service.md`](v2/user-service.md) |
| content | [`v2/content-service.md`](v2/content-service.md) |
| social | [`v2/social-service.md`](v2/social-service.md) |
| notification | [`v2/notification-service.md`](v2/notification-service.md) |
| steam | [`v2/steam-service-api-inventory.md`](v2/steam-service-api-inventory.md) |
| recommend | [`v2/recommend-service.md`](v2/recommend-service.md) |
| search | [`v2/search-service.md`](v2/search-service.md) |
| shop | [`v2/shop-service.md`](v2/shop-service.md) |
| game account | [`v2/game-account-service.md`](v2/game-account-service.md) |
| audit | [`v2/audit-service.md`](v2/audit-service.md) |
| AI 审核 | [`v2/ai-agent-service.md`](v2/ai-agent-service.md) |

## 维护规则

1. 新增或删除服务、公开 API、跨服务事件、数据库表或环境变量时，同一提交更新对应文档。
2. 文档中的端口、路径和配置必须能在 `application.yml`、Controller、Feign 或 SQL 中找到依据；不保留无法验证的推测。
3. 文档不得写入密钥、真实账号、生产域名、个人路径或生产数据。
4. 代码行为与旧文档冲突时，先修正文档索引和事实说明，再决定是否需要兼容实现。
