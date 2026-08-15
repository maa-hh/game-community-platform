# Level 3：函数级流转

> 按调用顺序列出**函数/方法链**，便于定位代码入口。

---

## B1. 发送验证码

| 序号 | 类.方法 | 文件 | 行号（约） |
|------|---------|------|------------|
| 1 | `AuthController.sendCode` | `AuthController.java` | L33-37 |
| 2 | `UserAuthServiceImpl.sendCode(phone, bizType)` | `UserAuthServiceImpl.java` | L68-110 |
| 3 | `SmsServiceImpl.sendVerificationCode` | `SmsServiceImpl.java` | L117-166 |
| 4 | `SmsServiceImpl.getTemplateCode` | 同上（私有） | L171-190 |
| 5 | `SmsServiceImpl.buildTemplateParam` | 同上（私有） | L196-198 |
| 6 | 阿里云 SDK `AsyncClient.sendSmsVerifyCode` | 外部 SDK | L143-144 调用处 |

**失败分支**：`handleCodeVerifyFailure` 仅在注册/改手机号验码时触发，不在 sendCode 链上。

---

## B2. 手机号注册

| 序号 | 类.方法 |
|------|---------|
| 1 | `AuthController.registerByPhone` |
| 2 | `UserAuthServiceImpl.registerByPhone` |
| 3 | `UserAuthServiceImpl.verifyCode`（私有） |
| 4 | `UserAuthServiceImpl.assignAccountIdFromPool`（私有） |
| 5 | `UserAuthServiceImpl.expandPool`（私有，号池耗尽时） |
| 6 | MyBatis `UserMapper.insert` / `UserAccountMapper.insert` / `UserAuthMapper.insert` |
| 7 | `AccountIdPoolMapper.casReserve` / `bindUserId` |

---

## B3. 登录

| 序号 | 类.方法 |
|------|---------|
| 1 | `AuthController.loginByAccount` |
| 2 | `CookieHelper.writeRefreshTokenCookie` |
| 3 | `UserAuthServiceImpl.loginByAccount` |
| 4 | `UserAuthServiceImpl.checkAndResolveAccountStatus` |
| 5 | `UserAuthServiceImpl.getUserAuth` |
| 6 | `EncryptUtils.checkPassword` |
| 7 | `UserAuthServiceImpl.handleLoginFailure`（失败时） |
| 8 | `UserSessionHelper.invalidateUserSession` |
| 9 | `UserSessionHelper.buildLoginVO` |
| 10 | `UserSessionHelper.generateAccessToken` / `generateRefreshToken` |
| 11 | `UserSessionHelper.saveSession` |
| 12 | `UserAuthServiceImpl.logOperation` |

---

## B4. Token 刷新

| 序号 | 类.方法 |
|------|---------|
| 1 | `AuthController.refreshToken` |
| 2 | `CookieHelper.extractRefreshToken` |
| 3 | `UserAuthServiceImpl.refreshToken` |
| 4 | `JwtUtils.parseToken(REFRESH_JWT_SECRET)` |
| 5 | `UserSessionHelper.getSession` / `isSessionOnline` |
| 6 | `UserSessionHelper.hashToken` |
| 7 | `UserSessionHelper.generateRefreshToken`（轮转） |
| 8 | `UserSessionHelper.saveSession` |

---

## B5. 登出

| 序号 | 类.方法 |
|------|---------|
| 1 | `AuthController.logout` |
| 2 | `UserAuthServiceImpl.logout` |
| 3 | `UserSessionHelper.invalidateSession` |
| 4 | `CookieHelper.clearRefreshTokenCookie` |

---

## A1. 网关鉴权

| 序号 | 类.方法 |
|------|---------|
| 1 | `JwtGlobalFilter.filter` |
| 2 | `JwtUtils.parseToken(ACCESS_JWT_SECRET)` |
| 3 | `RedisUtils.get(SESSION_PREFIX + sessionId)` |
| 4 | `RedisUtils.get(ACTIVE_SESSION_PREFIX + userId)` |
| 5 | 构造 `ServerHttpRequest` 添加 Header |

---

## E1. 文章发布审核

| 序号 | 类.方法 |
|------|---------|
| 1 | `ArticleController.createArticle` |
| 2 | `ArticleServiceImpl.createArticle` |
| 3 | `TaskServiceImpl.addImmediateTask` / `addDelayTask` |
| 4 | `ContentTaskScheduler.executePendingTasks` |
| 5 | `TaskServiceImpl.executeTask` → `doExecuteTask` |
| 6 | `ArticleAsyncServiceImpl.publishArticleAsync` |
| 7 | `ArticleAuditServiceImpl.auditArticle` |
| 8 | `ArticleContentServiceImpl.saveContent`（Mongo） |
| 9 | `ArticleSearchSyncProducer.send` |
| 10 | `SocialFeignClient.publishFeed` |

---

## F1. 发表评论

| 序号 | 类.方法 |
|------|---------|
| 1 | `SocialController.createComment` |
| 2 | `SocialServiceImpl.createComment` |
| 3 | `SocialCommentMapper.insert` + Mongo 存正文 |
| 4 | `ArticleBehaviorProducer.send` |
| 5 | `NotificationEventProducer.send` |

---

## G1. 通知入库 + 推送

| 序号 | 类.方法 |
|------|---------|
| 1 | `NotificationEventConsumer.onMessage` |
| 2 | `NotificationServiceImpl.handleEvent` |
| 3 | `NotificationMessageMapper.insert` |
| 4 | `NotificationUserStateMapper.update` |
| 5 | `SseServiceImpl.sendToUser` |

---

## 下游服务 Filter 链（content/social/notification 通用）

| 序号 | 类.方法 |
|------|---------|
| 1 | `UserFilter.doFilter` → `UserThreadLocal` |
| 2 | `AuthAspect`（`@LoginCheck` / `@AdminCheck`） |
