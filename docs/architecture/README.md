# 后端架构总览

本文是后端架构的唯一入口，内容以当前 Maven 模块、Controller、配置和 SQL 为准。服务级细节见 [`../README.md`](../README.md) 中的 `docs/v2` 文档。

## 1. 模块边界

```text
gateway
  └── service/* ──> model / common / feign / utils
                         └── MySQL / Redis / Kafka / Nacos / 外部服务
```

- `model` 只放跨模块数据模型，不放业务逻辑。
- `common` 只放跨服务稳定协议、常量和基础鉴权能力。
- `feign` 只放服务间客户端；提供方 Controller 留在对应服务。
- `service/*` 只实现本领域 Controller、Service、Mapper、事件和配置。
- `gateway` 是外部入口；服务间调用使用 `/feign/<domain>/**`，不与公开 API 混用。
- `sql` 是数据库变更的可审计入口，顺序由 `scripts/db/migrations.order` 管理。

## 2. 服务清单

| 服务 | 端口 | 公开前缀 | 主要职责 |
|---|---:|---|---|
| gateway | 8080 | `/` | 路由、JWT、CORS、内部鉴权 |
| user-service | 8081 | `/user/**` | 认证、资料、账号生命周期、装扮 |
| content-service | 8082 | `/article/**`、`/category/**`、`/file/**` | 文章、分类、上传、发布审核 |
| steam-service | 8083 | `/steam/**`、`/game/**` | Steam 游戏、评论、成就、跟随 |
| social-service | 8084 | `/social/**`、`/report/**` | 评论、回复、点赞、收藏、关注、举报 |
| shop-service | 8085 | `/shop/**` | 商品、库存、订单、积分和购买 |
| recommend-service | 8086 | `/hot-article/**` | 热榜、行为事件和推荐快照 |
| search-service | 8087 | `/search/**` | Elasticsearch 文章/游戏/建议词搜索 |
| audit-service | 8091 | `/audit/**` | 人工审核工单和审核通知 Outbox |
| notification-service | 8092 | `/notification/**` | 站内信、未读数、SSE 和通知事件 |
| ai-agent-service | 8093 | `/ai/**` | 文本、图片、文章内容审核和模型适配 |
| danmaku-service | 8094 | `/danmaku/**` | 弹幕写入、查询和实时广播 |

## 3. 典型调用链

### 外部请求

```text
前端 → gateway → JWT/白名单/内部鉴权 → 领域 Controller
     → Service → Mapper/Feign/Redis/Kafka → Result/VO
```

Controller 只负责绑定、鉴权注解和一次 Service 转发。公开请求和响应使用 DTO/VO；数据库内部自增 ID、中间件对象和内部凭证不得进入公开协议。

### 内容发布

```text
content-service
  → 本地敏感词/AI 审核
  → MySQL 元数据 + MongoDB 正文 + MinIO 媒体
  → Kafka 发布事件
  → search/recommend/social/notification 消费
```

### 社交和通知

```text
social/audit/user
  → 事务内写业务状态或 Outbox
  → Kafka
  → notification-service 幂等消费
  → Redis/SSE 推送在线用户
```

跨实例竞争使用数据库 CAS、幂等键或 Redis 共享状态；不要为简单同步流程再叠加额外重试线程。

## 4. 基础设施和配置

本地基础设施由根目录 `docker-compose.yml` 描述。服务配置优先从环境变量读取，开发默认值仅用于本地启动；生产必须显式注入：

- `MYSQL_*`、`REDIS_*`、`NACOS_*`
- `JWT_ACCESS_SECRET`、`JWT_REFRESH_SECRET`、`GATEWAY_INTERNAL_SECRET`
- `SMTP_*`、`STEAM_WEB_API_KEY`、`DASHSCOPE_API_KEY`、`DEEPSEEK_API_KEY`
- `MINIO_*`、`SEARCH_ES_*`、`KAFKA_*`、`XXL_JOB_ACCESS_TOKEN`

真实值只放 `.env` 或部署密钥管理系统。`.env.example` 只提供变量名和占位符。

## 5. 数据库变更

1. 在 `sql/` 新增可重入迁移或更新对应全量表结构。
2. 在 `scripts/db/migrations.order` 登记执行顺序。
3. 需要集成测试时同步 `src/test/resources/schema.sql`。
4. SQL 文件统一 UTF-8/utf8mb4，并在含中文的脚本中显式设置客户端字符集。
5. 运行对应服务测试和全仓编译后再提交。

## 6. 交付检查

```bash
git diff --check
mvn -DskipTests compile
mvn test
```

提交前还需确认 `git status --short --ignored` 中的 `.env`、日志、`target/`、本地备份和运行数据均未进入提交；测试工具中的账号、域名和令牌必须是占位符。
