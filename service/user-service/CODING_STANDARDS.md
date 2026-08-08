# user-service 附录规范

> **通用规范（必读）**：[`../CODING_STANDARDS.md`](../CODING_STANDARDS.md) — 适用于 `service/` 下所有微服务。  
> 本文仅补充 **user-service 领域**约定；包结构、分层、API 前缀、Feign、测试等以母规范为准。

产品设计：`docs/v2/user-service.md`、`docs/user-service-backend-prd.md`

---

## 1. 包结构（user 域）

```
com.game.community.user
├── controller/          # 对外 /user/**
├── feign/               # /feign/user/**
├── service/ + impl/
├── mapper/
├── common/              # 本服务内复用
│   ├── session/         # UserSessionHelper
│   └── verification/    # VerificationCodeHelper
├── audit/               # UserAuditHelper
├── event/、runner/、aspect/、filter/、config/
```

| 写什么 | 放哪 |
|--------|------|
| ≥2 个 Service 复用的用户能力 | `common/session` / `common/verification` / 明确的领域组件 |
| 仅审核流 | `audit/` |

### 1.1 user-service 简化原则

本服务严格遵守母规范中的“先直写、后抽取”：

- Controller 只做参数接收、鉴权和一次 Service 调用；不要在 Controller 中组装多个查询、做 ID 映射或拼装响应。
- Service 方法负责完整、可读的业务主流程；少于 3 行且只是转调的方法默认删除，不能为了“分层”制造 `Support`、`Helper`、`Facade` 或 `Delegate`。
- 邮箱、验证码、accountId 等参数在入口完成一次规范化；内部调用使用规范化后的参数，不重复 `normalize`、查用户或校验同一个输入。
- 对象只是同名字段复制时使用 `BeanUtils.copyProperties`；存在脱敏、默认值、枚举转换、字段审核或权限判断时再手工处理。
- 审核线程池的执行类只保留任务提交、领取、审核、状态落库和失败处理等真实职责；不再保留只转调的 `finishXxx` 方法。
- 删除类、接口、函数或配置前先用 `rg` 搜索调用方；删除后再次搜索旧名称，确认没有死代码和残留引用。

### 1.2 常量与配置

- `model.enums.user.*`：审核状态、字段类型、业务类型、操作类型等有限集合。
- `common.constant.user.*`：跨 user-service 类共享的 Redis 前缀、锁键、固定限制、用户领域字符串；按领域拆分，禁止一个常量类收纳所有内容。
- `common.constant.email.*`：邮件主题、正文模板等跨邮件发送流程复用的固定文案。
- `application.yml` + `@ConfigurationProperties`：线程池大小、队列容量、超时、数据库/SMTP/Nacos 地址等部署相关参数。
- 业务字符串、错误文案、TTL、锁时长、限流次数不得直接散落在 Service/Controller；能用枚举或已有常量就不能重新声明。
- 业务类中不保留新的 `static final` 固定值；即使只有一个类使用，也放入对应领域的专门常量类。

### 1.3 函数注释执行要求

- user-service 每个函数开头必须有简洁 JavaDoc，说明作用；公开函数补充关键参数、返回值和异常语义。
- 函数内部对规范化、事务、锁/CAS、数据库写入、线程池提交、外部调用、失败回滚和资源清理等关键步骤写必要注释。
- 不给 getter、纯 Bean 映射和一眼可懂的简单语句添加逐行废话注释；注释解释原因和业务意图，而不是重复代码。

### 1.4 本服务已统一的异步和 ID 约定

- 邮件接口快速返回，`EmailTaskExecutor` 在邮件线程池执行 SMTP；不在请求线程调用 Future `get()`，不增加邮件 `sleep` 重试或定时扫描。
- 审核任务先落 `t_user_audit_task`，再提交 `AuditTaskExecutor`；线程池执行器内部完成领取、审核、状态更新和失败回滚，业务 Service 只提交任务。
- Kafka 通知使用 Kafka 自带重试；发送回调失败写 `t_user_notification_outbox` 失败记录并告警，不再增加独立通知扫描线程。
- `t_user.id` 是内部关联键，`accountId` 是对外键；按账号批量查询时一次完成映射和装扮查询，避免循环查询用户和装扮。
- 用户背包查询使用分页和条件筛选，至少支持装扮效果类型、分类、已装备状态、有效状态和关键词；库存查询只返回 `quantity > 0` 的记录。

---

## 2. API 映射（前缀 `/user`）

| 业务 | Controller | 类级路径 | 示例 |
|------|------------|----------|------|
| 认证 | `AuthController` | `/user/auth` | `POST /user/auth/login` |
| 资料 | `UserProfileController` | `/user` | `GET /user/me` |
| 改邮箱 | `UserProfileController` | `/user/email` | `POST /user/email/send-old-code` |
| 账户 | `UserAccountController` | `/user` | `POST /user/cancel` |
| 查询 | `UserQueryController` | `/user` | `GET /user/{accountId}` |
| 内部 | `UserFeignController` | `/feign/user` | `GET /feign/user/{userId}/account` |

网关：`/user/**`；认证白名单 `/user/auth/*`（`GatewayConstants.AUTH_PUBLIC_PATH_PREFIXES`）。  
前端：`game-community/src/service/auth.ts`、`profile.ts`、`account.ts`。

---

## 3. Service 职责

| Service | 职责 |
|---------|------|
| `UserAuthService` | 注册、登录、刷新、登出、找回密码、发码（REGISTER/RESET/CANCEL） |
| `UserProfileService` | 资料、改密、改邮箱、字段审核提交 |
| `UserAccountService` | 封禁、解封、注销、撤销、刷新账号状态 |
| `UserQueryService` | 只读查询、搜索、Feign 账户状态 |
| `UserFieldAuditTaskService` | 审核任务入队与执行 |
| `EmailService` | 发信 |

---

## 4. 表职责

| 表 | 职责 |
|----|------|
| `t_user` | 已通过资料 |
| `t_user_account` | 账号状态 / 生命周期 |
| `t_user_profile_audit` | 字段审核 + pending |
| `t_user_auth` | 密码 |
| `t_user_audit_task` | 审核任务流水 |
| `t_user_operation_log` | 操作审计 |

审核 pending **不得**写回 `t_user`。

---

## 5. 枚举与空值

- 枚举：`model.enums.user.*`（`CodeBizType`、`AuditFieldType`、`FieldAuditStatus`、`UserAccountStatus`、`UserStrings` 等）
- `UserConstants` **仅**数值/时间配置
- 字符串入库：优先 `UserStrings.EMPTY`；时间字段可为 `null`

---

## 6. 认证与会话

- Access Token：响应体；Refresh：HttpOnly Cookie（Controller 用 `CookieHelper` 写/清）
- 会话 Redis：`UserSessionHelper`；上下文：`UserFilter` → `UserThreadLocal`，Service 在需要登录的业务入口校验 userId
- 密码：仅 BCrypt

---

## 7. 验证码与邮件

- 统一：`VerificationCodeHelper.sendEmailCode(email, CodeBizType)`
- TTL：`email.code.expire-seconds`
- 改邮箱：仅 `/user/email/*`；注销发码：仅 `/user/cancel/send-code`；`/user/auth/send-code` 仅 REGISTER / RESET_PASSWORD

---

## 8. 字段审核

`UserProfileService` → `UserAuditHelper` CAS → `t_user_audit_task` → `UserFieldAuditTaskService`  
线程池满：回滚 + `t_user_audit_reject_log` + 429。  
文案：`AuditFieldType.label()` / `busyMessage()`。

---

## 9. 测试

```bash
mvn -pl service/user-service -am clean test
```

- 基类：`AbstractUserServiceIntegrationTest`
- `@MockBean UserFieldAuditTaskService`
- SQL：`sql/user.sql` + `src/test/resources/schema.sql`

---

## 10. user 域禁止项（补充母规范）

- ❌ 认证路径不用 `/user/auth` 前缀
- ❌ `UserConstants` 业务字符串常量
- ❌ 审核 pending 写入 `t_user`
- ❌ MD5 密码路径

---

## 11. user 新增功能检查表

- [ ] 已读 [`../CODING_STANDARDS.md`](../CODING_STANDARDS.md) 通用项
- [ ] 函数均有开头说明，关键执行点有必要注释
- [ ] 少于 3 行的转调函数、无调用方函数和无意义抽象已删除
- [ ] 常量已按枚举 / common 常量 / 类内常量 / 配置项正确归类
- [ ] 未引入无明确需求的额外线程池、调度器、Outbox、Future 或重试层
- [ ] API 在 `/user/...` 或 `/feign/user/...`
- [ ] 枚举 + `UserStrings.EMPTY`
- [ ] 网关白名单 / 前端 service 已同步
- [ ] `mvn -pl service/user-service -am test` 通过
