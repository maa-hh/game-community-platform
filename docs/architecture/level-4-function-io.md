# Level 4：函数输入 / 输出 / 功能说明

> 每个函数的契约层文档，不含实现细节（见 Level 5）。

---

## B1. 发送验证码

> L3 函数链共 6 步，以下 **全部** 给出 IO 说明。

### ① `AuthController.sendCode(SendCodeDTO dto)`
| 项 | 说明 |
|----|------|
| **输入** | HTTP Body JSON → `SendCodeDTO`（经 `@Valid` 校验后的 `phone`、`bizType`） |
| **输出** | `Result<Void>`，`code=200`，`data=null` |
| **功能** | REST 入口；将 DTO 拆为两个参数委托 `UserAuthService.sendCode`，不做业务逻辑 |

### ② `UserAuthServiceImpl.sendCode(String phone, String bizType)`
| 项 | 说明 |
|----|------|
| **输入** | `phone` 已校验的手机号；`bizType` 业务场景字符串 |
| **输出** | `void`；频控/锁定失败抛 `BusinessException`（消息见 L5） |
| **功能** | 三层 Redis 频控 → 生成 6 位码写 Redis → 写 cooldown/daily → 调短信 |

### ③ `SmsServiceImpl.sendVerificationCode(String phone, String code, String bizType)`
| 项 | 说明 |
|----|------|
| **输入** | 目标手机号；服务端生成的 6 位 `code`；`bizType`（用于选模板） |
| **输出** | `void`；阿里云返回非 OK 抛 `RuntimeException` |
| **功能** | mock/降级判断 → 组装阿里云请求 → 同步等待发送结果 |

### ④ `SmsServiceImpl.getTemplateCode(String bizType)`（私有）
| 项 | 说明 |
|----|------|
| **输入** | `bizType` 字符串（大小写不敏感，`null` 当 LOGIN 处理） |
| **输出** | 阿里云模板 CODE 字符串（100001–100005，可配置） |
| **功能** | 将业务场景映射到短信模板编号 |

### ⑤ `SmsServiceImpl.buildTemplateParam(String code)`（私有）
| 项 | 说明 |
|----|------|
| **输入** | 6 位验证码字符串 |
| **输出** | JSON 字符串，如 `{"code":"123456","min":"5"}` |
| **功能** | 填充阿里云模板变量 `${code}`、`${min}`（有效分钟数来自配置） |

### ⑥ `AsyncClient.sendSmsVerifyCode(SendSmsVerifyCodeRequest)`（阿里云 SDK）
| 项 | 说明 |
|----|------|
| **输入** | `signName`、`templateCode`、`templateParam`、`phoneNumber` |
| **输出** | `SendSmsVerifyCodeResponse`；`body.code=="OK"` 为成功 |
| **功能** | 调用 `dypnsapi.aliyuncs.com` 向用户手机下发短信（不含验码逻辑，码由服务端持有） |

---

## B2. 注册

### `UserAuthServiceImpl.registerByPhone(RegisterDTO dto)`
| 项 | 说明 |
|----|------|
| **输入** | 用户名、密码、手机号、验证码、可选 steamAccount |
| **输出** | `Long accountId` |
| **功能** | 验码 → 防重锁 → 三表写入 → 号池分配 |

### `UserAuthServiceImpl.verifyCode(String phone, String inputCode)`（私有）
| 项 | 说明 |
|----|------|
| **输入** | 手机号；用户提交的验证码 |
| **输出** | `void`；成功删 Redis 验证码 |
| **功能** | 失败累加计数，5 次锁定 30 分钟 |

### `UserAuthServiceImpl.assignAccountIdFromPool(User user)`（私有）
| 项 | 说明 |
|----|------|
| **输入** | 已 insert 的 User 实体 |
| **输出** | `void`（副作用：回填 user.accountId） |
| **功能** | CAS 抢占号池最小可用 accountId，失败重试 3 次 |

---

## B3. 登录

### `UserAuthServiceImpl.loginByAccount(AccountLoginDTO dto, String loginIp)`
| 项 | 说明 |
|----|------|
| **输入** | accountId、password、可选 type；客户端 IP |
| **输出** | `LoginVO` |
| **功能** | 账号状态检查 → 密码验证 → 单设备会话 → 登录日志 |

### `UserSessionHelper.buildLoginVO(User, UserAccount, sessionId)`
| 项 | 说明 |
|----|------|
| **输入** | 用户实体、账号实体、新 UUID sessionId |
| **输出** | `LoginVO`（含 accessToken，refreshToken 在 VO 内但 JSON 忽略） |
| **功能** | 生成双 Token、写 Redis 会话 |

### `UserSessionHelper.saveSession(sessionId, userId, UserSessionVO)`
| 项 | 说明 |
|----|------|
| **输入** | sessionId；userId；会话快照（含 refreshTokenHash、status=ONLINE） |
| **输出** | `void` |
| **功能** | 写 session 与 active-session 两个 Redis Key |

---

## B4. 刷新 Token

### `UserAuthServiceImpl.refreshToken(String refreshToken)`
| 项 | 说明 |
|----|------|
| **输入** | refresh JWT 字符串 |
| **输出** | `TokenRefreshVO`（新 accessToken + refreshToken 写 Cookie） |
| **功能** | 校验会话有效 → 轮转 refresh → 防并发锁 |

### `UserSessionHelper.hashToken(String token)`
| 项 | 说明 |
|----|------|
| **输入** | refreshToken 明文 |
| **输出** | SHA256(token + REFRESH_JWT_SECRET) 十六进制 |
| **功能** | 会话内只存 Hash，防 Redis 泄露后直接可用 |

---

## A1. 网关

### `JwtGlobalFilter.filter(ServerWebExchange, GatewayFilterChain)`
| 项 | 说明 |
|----|------|
| **输入** | 原始 HTTP 请求 |
| **输出** | 继续链路或 401 Response |
| **功能** | 白名单跳过；否则 JWT+Redis 双校验后注入 Header |

---

## E1. 内容任务

### `TaskServiceImpl.addImmediateTask(int type, T param, Long businessId)`
| 项 | 说明 |
|----|------|
| **输入** | 任务类型；JSON 参数；业务 ID（如 articleId） |
| **输出** | `Long taskId` |
| **功能** | 落库 t_task → 提交后入 Redis 立即队列 |

### `TaskServiceImpl.executeTask(Task task)`
| 项 | 说明 |
|----|------|
| **输入** | Task 实体 |
| **输出** | `void` |
| **功能** | 更新状态 → 调 ArticleAsyncService → 写 task_log |

---

## F1. 社交

### `SocialServiceImpl.createComment(CommentDTO dto)`
| 项 | 说明 |
|----|------|
| **输入** | articleId、content、parentId 等 |
| **输出** | `CommentVO` |
| **功能** | 写 MySQL+Mongo → 发行为/通知 Kafka |

### `FollowServiceImpl.follow(Long targetUserId)`
| 项 | 说明 |
|----|------|
| **输入** | 被关注用户 ID（ThreadLocal 取当前用户） |
| **输出** | `void` |
| **功能** | 写关注表 → 回填 Feed → 通知 |

---

## G1. 通知

### `NotificationServiceImpl.handleEvent(NotificationEvent event)`
| 项 | 说明 |
|----|------|
| **输入** | Kafka 反序列化事件（type、receiverId、payload） |
| **输出** | `void` |
| **功能** | 幂等入库 → 更新未读数 → 触发 SSE |

### `SseServiceImpl.connect(Long userId)`
| 项 | 说明 |
|----|------|
| **输入** | 当前用户 ID |
| **输出** | `SseEmitter`（长连接） |
| **功能** | 注册内存 emitter；断连清理 |

---

## 评价维度（函数层）

| 函数 | 优点 | 风险点 |
|------|------|--------|
| `verifyCode` | 与注册锁分离，支持多次试错 | 锁定后需等 30 分钟 |
| `hashToken` | refresh 不明文存 Redis | 密钥轮换需迁移 session |
| `assignAccountIdFromPool` | CAS 防并发抢号 | 号池耗尽需扩容 |
| `JwtGlobalFilter` | 统一鉴权 | 公开 GET 也要求 Token（产品层需知） |
