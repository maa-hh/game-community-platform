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
│   ├── support/         # UserSupport
│   ├── session/         # UserSessionHelper
│   └── verification/    # VerificationCodeHelper
├── audit/               # UserAuditHelper
├── event/、runner/、aspect/、filter/、config/
```

| 写什么 | 放哪 |
|--------|------|
| ≥2 个 Service 复用的用户能力 | `common/support` / `session` / `verification` |
| 仅审核流 | `audit/` |

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
- 会话 Redis：`UserSessionHelper`；上下文：`UserFilter` → `UserSupport.requireUserId()`
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
- [ ] API 在 `/user/...` 或 `/feign/user/...`
- [ ] 枚举 + `UserStrings.EMPTY`
- [ ] 网关白名单 / 前端 service 已同步
- [ ] `mvn -pl service/user-service -am test` 通过
