# content-service 商业级整改未闭环清单

> 盘点日期：2026-08-04  
> 范围：content-service，以及与内容发布、上传、搜索、审核、社交 Feed 直接相关的 model/common/utils 和数据库脚本。  
> 目的：只记录当前仍未实现、部分实现或缺少生产验收的项目，后续按优先级统一处理。

## 状态说明

- **未实现**：当前代码没有对应能力。
- **部分实现**：主流程已有代码，但还没有达到生产级闭环。
- **待验收**：代码已具备，但缺少真实依赖、故障注入或压力验证，不能视为已完成。

本清单不重复列出已经完成的项目：公开帖子 ID 基础迁移、任务租约与 CAS、Mongo upsert、分片 MD5 校验、Outbox 基础投递、关系表和连接池/超时配置等已落地，但其中部分仍有下方的生产闭环工作。

## P0：上线前必须补齐

### P0-01 缺少真实依赖集成测试和故障注入验收

- **状态**：未实现
- **证据**：content-service 当前没有测试源；现有验证主要是编译和配置解析。后端测试命令为 `mvn -pl service/content-service -am test`，但没有覆盖 MySQL、MongoDB、Redis、Kafka、MinIO 的真实交互。
- **风险**：数据库迁移、事务边界、Redis CAS、Kafka Outbox、MinIO 合并/删除等关键路径可能只在编译层面正确，运行时失败会造成帖子状态、正文、媒体或消息不一致。
- **后续处理**：增加 Testcontainers 或等价集成环境，至少覆盖：
  1. 新建/更新/发布/驳回/删除文章；
  2. MySQL 事务回滚与 Mongo upsert；
  3. 两个 Worker 抢同一任务、租约过期、取消与重试；
  4. 多分片并发上传、暂停恢复、重复上传、MD5 错误；
  5. Kafka 不可用、Outbox 重启恢复、死信；
  6. MinIO 合并失败、删除失败、重复清理。
- **验收标准**：测试能启动真实依赖并验证最终一致性；至少加入一次进程重启和一次网络/依赖故障场景。

### P0-02 生产环境仍允许使用危险默认凭据

- **状态**：未实现
- **证据**：[application.yml](/Users/ma/IdeaProjects/game-community-platform/service/content-service/src/main/resources/application.yml:19) 默认 MySQL 密码为 `root123`；[application.yml](/Users/ma/IdeaProjects/game-community-platform/service/content-service/src/main/resources/application.yml:104)–[105] 默认使用 MinIO 管理员凭据。
- **风险**：误用默认配置可能直接导致数据库和对象存储被接管；当前没有生产 profile 的必填校验，也没有启动时拒绝默认凭据的保护。
- **后续处理**：
  1. 开发默认值与生产配置彻底分离；
  2. 生产环境密码、Access Key、Secret Key 只从 Secret/Vault 注入；
  3. 启动时校验禁止 `root123`、`minioadmin` 等默认值；
  4. MinIO 改用最小权限的专用用户；
  5. 禁止将生产连接串写入 Nacos 明文配置。
- **验收标准**：生产环境缺少密钥或使用默认值时启动失败；应用账号不能执行不必要的管理操作。

## P1：上线后首个迭代必须完成

### P1-01 分页仍以 offset 为主，未完成统一 Keyset 分页

- **状态**：部分实现
- **证据**：[ArticleServiceImpl.java](/Users/ma/IdeaProjects/game-community-platform/service/content-service/src/main/java/com/game/community/content/service/impl/ArticleServiceImpl.java:604)–[747] 的公开列表、个人列表、关注列表和管理列表仍使用 MyBatis-Plus `Page`；[ArticleGameMapper.java](/Users/ma/IdeaProjects/game-community-platform/service/content-service/src/main/java/com/game/community/content/mapper/ArticleGameMapper.java:30) 仍使用 `LIMIT/OFFSET`。只有“更多文章”路径使用游标条件。
- **风险**：数据量增大后深分页扫描成本高，发布/删除并发时 offset 结果可能重复或漏项。
- **后续处理**：统一使用 `(published_time, id)` 或 `(update_time, id)` 游标；保留旧 page 参数一段兼容期，内部转换为 cursor；管理后台再单独保留带总数的 offset 分页。

### P1-02 上传会话元数据只在 Redis，缺少持久化和全局清理

- **状态**：部分实现
- **证据**：[ChunkUploadServiceImpl.java](/Users/ma/IdeaProjects/game-community-platform/service/content-service/src/main/java/com/game/community/content/service/impl/ChunkUploadServiceImpl.java:350) 起，上传会话、MD5 索引和文章绑定关系都保存在 Redis；MinIO 分片前缀主要在主动中止、删除文章或合并时清理。
- **风险**：Redis 淘汰、故障或数据恢复后，分片对象可能成为孤儿；无法可靠统计用户配额、上传占用和长期未完成会话。
- **后续处理**：增加 MySQL `t_file_upload_session` 或独立上传会话表，Redis 只做热状态；增加定时扫描器清理超时会话和 MinIO 孤儿前缀；增加用户/租户配额、并发上传数和单文件数限制。

### P1-03 Outbox 只有投递重试，缺少运营闭环和统一事件幂等协议

- **状态**：部分实现
- **证据**：[ContentOutboxPublisher.java](/Users/ma/IdeaProjects/game-community-platform/service/content-service/src/main/java/com/game/community/content/event/ContentOutboxPublisher.java:39) 已实现轮询、抢占和重试；但事件 payload 没有统一 `eventId`/版本字段，缺少死信查询、重放、暂停和告警接口。部分消费者通过业务字段去重，通知侧还是时间窗口判断。
- **风险**：网络超时可能产生重复副作用；死信只能停留在数据库中，无法由运营人员安全恢复；消费者去重规则不统一会出现重复 Feed、重复通知或误去重。
- **后续处理**：
  1. 所有事件统一携带不可变 `eventId`、`eventType`、`occurredAt`、`schemaVersion`；
  2. 消费者建立按 eventId 或业务幂等键的唯一约束；
  3. 增加 Outbox 状态、延迟、死信、重试次数指标；
  4. 增加受权限保护的查询、重放和人工确认接口；
  5. 明确 Kafka 和远程 Feed 的 at-least-once 语义。

### P1-04 MySQL、Mongo、MinIO 之间没有对账/修复任务

- **状态**：未实现
- **证据**：删除和发布已改成 Outbox/异步清理，但没有扫描 `t_article`、Mongo 正文和 MinIO 对象的定期对账程序。
- **风险**：跨存储事务无法原子提交。异常中断后可能出现“文章已发布但正文缺失”“数据库已删除但对象残留”“公共媒体存在但文章引用不存在”。
- **后续处理**：按 `articleId` 和 object key 建立可重入对账任务；支持只读报告、自动修复、人工确认和限速；发布前后分别校验 MySQL/Mongo/MinIO 状态。

### P1-05 高 QPS 保护尚未覆盖 content-service 全部入口

- **状态**：部分实现
- **证据**：网关已有部分 Sentinel 规则，但 content-service 内未发现统一的接口级 `@SentinelResource`、上传并发限制、用户级配额或统一的请求 size 上限；分页接口多数直接接受外部 `size`，部分查询使用 `Math.max(size, 1)`，没有统一最大值。
- **风险**：大分页、批量 ID、重复上传、恶意图片/视频请求可能放大数据库、Redis、MinIO 和线程池压力。
- **后续处理**：
  1. 统一分页、批量 ID、正文、图片数量和上传请求上限；
  2. 为公开列表、详情、上传、发布、管理接口设置不同限流和并发舱壁；
  3. 增加用户级上传配额、单 IP/用户频率限制；
  4. 对拒绝、排队、超时和线程池饱和增加指标。

### P1-06 公开 ID 已接入主链路，但系统仍存在数字 ID 兼容分支

- **状态**：部分实现
- **证据**：前端多个映射仍使用 `publicId || String(id)`；[ArticleController.java](/Users/ma/IdeaProjects/game-community-platform/service/content-service/src/main/java/com/game/community/content/controller/ArticleController.java:176) 起的管理接口仍使用内部 Long ID；社交通知和部分服务间数据仍携带 `articleId` 数字字段。
- **风险**：新旧 ID 语义混用，容易把数据库主键暴露到链接、通知或转发数据中；跨服务接口无法明确判断参数到底是 public ID 还是内部 ID。
- **后续处理**：
  1. 对外 VO、前端路由、分享、转发、通知统一使用 `articlePublicId`；
  2. 内部服务保留 `articleId` 但只允许服务间内部接口使用；
  3. 删除所有 `publicId || String(id)` 的生产兼容 fallback；
  4. 为旧数字链接明确返回 404，不做隐式兼容；
  5. 更新 Feign、社交、通知、搜索接口契约和数据库迁移校验。

### P1-07 外部依赖保护未形成统一策略

- **状态**：部分实现
- **证据**：Feign/RestTemplate 已有超时、有限重试和 Sentinel 配置；但 AI 审核、Mongo、MinIO、Kafka 的业务级超时、隔离、降级结果、重试预算和告警没有统一配置模型。
- **风险**：AI 或对象存储变慢时，审核线程、任务线程和连接池可能持续堆积；局部超时并不等价于系统级熔断和背压。
- **后续处理**：按依赖分别定义 timeout、bulkhead、retry budget、fallback 和最大排队时间；AI 审核失败进入明确的人工审核/重试状态，不依赖线程池自然耗尽。

### P1-08 数据约束和空值规范尚未完全收敛

- **状态**：部分实现
- **证据**：[content.sql](/Users/ma/IdeaProjects/game-community-platform/sql/content.sql:1) 仍有摘要、媒体、审核原因、计划时间等可空字段；状态值、文章类型、审核阶段主要依赖 Java 常量，没有完整的数据库 CHECK/枚举约束和统一字段默认策略。
- **风险**：不同入口可能写入空字符串、非法状态或不一致的 JSON；查询层需要大量兼容判断。
- **后续处理**：区分“业务上可选”的 NULL 与“必须有值”的字段；对状态/类型/版本/排序字段设置默认和校验；统一 JSON schema、字符串长度和空字符串归一化；迁移前先做脏数据扫描。

### P1-09 缺少数据库迁移的冲突预检和线上执行保护

- **状态**：部分实现
- **证据**：[content-production-hardening.sql](/Users/ma/IdeaProjects/game-community-platform/sql/content-production-hardening.sql:73) 会为活动任务增加唯一索引；如果历史上已存在同一业务的多个活动任务，迁移会失败。脚本有部分幂等判断，但没有迁移锁、冲突处理、备份/回滚策略。
- **风险**：生产库迁移中断，或者在大表上长时间锁表，影响内容写入。
- **后续处理**：迁移前输出重复活动任务、重复 public_id、孤立转发引用、重复分类关系报告；明确保留规则后再清理；为大表索引采用在线 DDL，并增加 migration version/lock 机制。

## P2：稳定性和长期演进阶段处理

### P2-01 技术文档没有同步到最终实现

- **状态**：未实现
- **证据**：[docs/v2/content-service.md](/Users/ma/IdeaProjects/game-community-platform/docs/v2/content-service.md:1) 仍按旧的 `t_article`、任务字段和上传描述编写，未完整说明 `public_id`、Outbox、租约、关系表、MD5 恢复和 Actuator 安全配置。
- **后续处理**：以当前代码和迁移脚本为准重写数据模型、状态机、接口参数、失败恢复和部署配置，并增加文档版本号。

### P2-02 缺少容量模型和压力基线

- **状态**：未实现
- **后续处理**：针对文章列表、详情、发布审核、分片上传、Outbox 投递分别建立压测场景，记录 P50/P95/P99、数据库连接占用、Redis 命中率、Kafka 延迟、MinIO 吞吐和线程池拒绝率，形成容量与扩容阈值。

### P2-03 缺少完整的业务指标、链路追踪和告警面板

- **状态**：部分实现
- **证据**：Actuator 已暴露 metrics/prometheus，但目前没有看到 content-service 专用业务指标和告警规则。
- **后续处理**：增加发布成功率、审核耗时、任务重试/死信、Outbox 延迟、上传失败率、MD5 失败率、Mongo/MinIO 清理残留、Feign 熔断次数等指标，并接入 traceId 和告警平台。

### P2-04 缺少死信、孤儿媒体和任务的运维操作界面

- **状态**：未实现
- **后续处理**：提供管理员/运维专用查询、重放、终止、清理和导出接口；所有操作记录操作者、原因和审计日志，禁止直接修改业务表绕过状态机。

### P2-05 缓存防击穿和本地热点治理尚未完整

- **状态**：部分实现
- **证据**：分类和游戏标签已有 Redis 缓存，但文章详情、热点文章、用户补全没有统一的缓存 TTL、single-flight、随机过期和降级策略。
- **后续处理**：按读场景定义缓存边界；对高热点详情增加短 TTL/本地缓存和请求合并；缓存失败必须回源且有速率保护，避免 Redis 故障时把压力全部转给数据库。

## 推荐处理顺序

1. **P0-02**：先移除生产默认凭据并增加启动失败校验。
2. **P0-01**：建立真实依赖集成测试和故障注入基线。
3. **P1-09**：执行数据库迁移前完成重复数据预检和线上 DDL 方案。
4. **P1-03/P1-04**：完善事件幂等、死信重放和跨存储对账。
5. **P1-01/P1-02/P1-05/P1-06/P1-07/P1-08**：统一分页、上传、限流、ID、依赖保护和数据约束。
6. **P2**：补齐文档、压测、监控和运维工具。

