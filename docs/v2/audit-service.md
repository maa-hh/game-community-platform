# audit-service

统一人工审核服务，负责举报、帖子人工审核和用户资料人工复核工单。

## 边界与接口

- 对外接口统一使用 `/audit/**`，管理员接口为 `/audit/moderation/**`。
- 管理端使用 `taskKey`（`public_id`）定位工单，数据库自增 `id` 仅在服务内部使用。
- 列表接口为 `GET /audit/moderation/page?page=1&size=20&status=0&taskType=REPORT`；分页和筛选由 `ModerationPageQueryDTO` 绑定，默认每页 20 条，最大 100 条。
- 详情、认领、处理分别为 `GET /audit/moderation/{taskKey}`、`POST /audit/moderation/{taskKey}/claim`、`PUT /audit/moderation/{taskKey}`。
- 处理请求必须携带 `claimToken` 和调用方生成的 `requestId`；同一工单、同一请求号重复提交是幂等成功。

## 工单状态与并发

```text
PENDING -> PROCESSING -> COMPLETED
             |   ^
             |   |
             +---+ 认领租约过期且尚未开始远程动作时回收
```

认领使用数据库版本号、状态、处理人和租约条件做 CAS 更新；远程动作开始前先以独立事务写入 `action_request_id`，防止重复点击和多实例并发执行。系统自动通过使用同一套认领租约，并在完成更新时再次校验系统处理人、令牌和版本号，只有 CAS 成功才允许发送通知。

Kafka 消费按 `taskType + sourceId` 幂等。`t_moderation_task.active_source_key` 只约束未完成任务，允许目标内容版本变化后创建新的审核工单。

## 可靠通知

审核完成后先写入 `t_audit_notification_outbox`，由各实例共同轮询；数据库 claim 条件保证同一条 Outbox 只有一个实例发布。锁租约默认为 10 分钟，超过租约才回收并增加重试次数，最多 10 次。通知 `eventId` 由工单、事件类型和收件人稳定生成，Kafka 重投和 Outbox 重试不会产生重复站内通知。

## 依赖与启动

audit-service 只依赖 MySQL、Kafka、Nacos、Feign 和 Sentinel；审核服务本身不使用 MongoDB、Redis 或 AI 模型，因此不再声明这些旧依赖或配置。

```bash
./scripts/services/start-audit-service.sh foreground
mvn -pl service/audit-service -am test
```

启动验证应在日志中看到 `Started AuditServiceApplication`，并确认 `GET /actuator/health` 返回成功。真实环境需要 MySQL、Kafka、Nacos 和被调用的 user/content/social/danmaku 服务可达。

## 数据库迁移

`scripts/db/migrations.order` 中按以下顺序执行统一审核表迁移：

1. `sql/moderation-task.sql`
2. `sql/moderation-task-hardening.sql`
3. `sql/moderation-task-public-id.sql`

`moderation-task-hardening.sql` 负责兼容旧表并补齐租约、幂等和多实例索引；旧的非幂等 `moderation-task-alter.sql` 已删除，避免与生产迁移重复执行。
