# 游戏社区平台后端

这是游戏社区平台的 Spring Boot 微服务后端，负责账号、内容、社交、通知、游戏数据、搜索、推荐、商城、弹幕、审核和 AI 内容审核等能力。前端项目位于同级目录的 `game-community` 仓库。

## 技术栈

- Java 17、Spring Boot 3、Spring Cloud Alibaba、Maven
- MySQL、Redis、Kafka、Nacos、Sentinel
- MongoDB（文章/评论正文）、MinIO（媒体文件）、Elasticsearch（搜索）
- MyBatis-Plus、OpenFeign、Springdoc

## 模块结构

| 路径 | 职责 |
|---|---|
| `gateway` | 网关、JWT 校验、路由和内部请求鉴权 |
| `service/*` | 领域微服务；每个服务独立启动 |
| `model` | DTO、VO、Entity、枚举和跨服务数据模型 |
| `common` | 跨服务常量、鉴权注解和公共错误处理 |
| `feign` | 服务间 Feign 客户端 |
| `utils` | Redis、JWT、邮件、MinIO 等基础设施 |
| `sql` | 全量建表、种子和增量迁移脚本 |
| `scripts` | 数据库同步、服务启动、接口验证和性能脚本 |
| `docs` | 当前实现对应的技术文档和运维说明 |

完整的模块、端口、调用边界和数据流见 [`docs/README.md`](docs/README.md) 与 [`docs/architecture/README.md`](docs/architecture/README.md)。

## 本地启动

1. 安装 JDK 17、Maven、Docker Compose。
2. 复制 `.env.example` 为 `.env`，填写本地数据库、JWT、内部密钥和第三方服务配置；真实 `.env` 不得提交。
3. 启动基础设施：

   ```bash
   docker compose up -d mysql redis nacos kafka mongodb elasticsearch minio
   ```

4. 按 [`scripts/db/migrations.order`](scripts/db/migrations.order) 执行 SQL，或使用数据库同步脚本。
5. 编译并启动网关及需要的服务：

   ```bash
   mvn -DskipTests package
   ./scripts/services/start-user-service.sh foreground
   ```

   其他服务的端口和启动脚本见架构文档。

## 验证

提交前至少执行：

```bash
mvn -DskipTests compile
mvn test
git diff --check
```

单服务开发优先执行 `mvn -pl service/<service-name> -am test`。涉及公共模块、Feign、网关、配置或 SQL 协议时，执行全仓编译和对应服务测试。

## 配置和隐私边界

- `.env`、日志、进程文件、Maven `target/`、本地备份和导出的运行数据不属于版本库。
- API 密钥、SMTP 授权码、JWT/内部密钥、Steam Web API Key、数据库真实密码只通过环境变量或部署平台注入。
- `scripts/http` 和 `scripts/apifox` 只保留占位符；测试账号请放在被忽略的 `http-client.private.env.json` 中。
- SQL 脚本可能包含开发种子数据，提交前确认不含真实用户邮箱、手机号、令牌或生产业务数据。

## 文档和演示视频

技术文档入口是 [`docs/README.md`](docs/README.md)。
