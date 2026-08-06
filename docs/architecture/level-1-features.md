# Level 1：功能清单（输入 / 输出 / 目的）

> 层级说明：本文件描述**每个功能做什么**。展开 Level 2 看流转，Level 3 看函数链，Level 4 看函数 IO，Level 5 看实现细节与评估。

---

## 模块 A：网关鉴权（gateway）

### A1. JWT 全局鉴权
| 项 | 说明 |
|----|------|
| **输入** | HTTP 请求；Header `Authorization: Bearer {accessToken}` 或 query `accessToken` |
| **输出** | 放行并注入 `X-User-Id` / `X-User-Type` / `X-Session-Id` / `X-Steam-Account`；或 401 JSON |
| **目的** | 统一入口鉴权，下游微服务无需重复解析 JWT |

**白名单（免 Token）**：`/user/sendCode`、`/user/register*`、`/user/login*`、`/user/token/refresh`

---

<!-- USER-SERVICE-AUTO -->
## 模块：user-service（自动生成）

### 模块 A · 用户认证

#### A1 · 发送验证码 POST /user/sendCode
| 项 | 说明 |
|----|------|
| **输入** | <strong>phone</strong>：<code>String</code>，大陆 11 位手机号<br><strong>bizType</strong>：<code>String</code>，REGISTER / LOGIN / CHANGE_PHONE / RESET_PASSWORD / BIND_PHONE / VERIFY_PHONE |
| **输出** | <code>Result&lt;Void&gt;</code>，data=null，不含验证码 |
| **目的** | 为注册、登录、改号等场景下发一次性短信验证码；三层频控 + Redis 存码 + 阿里云模板发送 |

#### A2 · 手机号注册 POST /user/register/phone
| 项 | 说明 |
|----|------|
| **输入** | <code>RegisterDTO</code>：username、password、phone、code、steamAccount? |
| **输出** | <code>Result&lt;Long&gt;</code> 对外 accountId |
| **目的** | 验码 → 防重锁 → 查重 → 写 user/account/auth 三表 → 号池 CAS 分配 accountId |

#### A3 · 账号密码登录 POST /user/login/account
| 项 | 说明 |
|----|------|
| **输入** | <code>AccountLoginDTO</code>：accountId、password、type? |
| **输出** | <code>Result&lt;LoginVO&gt;</code> + refreshToken Cookie |
| **目的** | 验账号状态与密码 → 单设备会话 → 双 Token |

#### A4 · 刷新令牌 POST /user/token/refresh
| 项 | 说明 |
|----|------|
| **输入** | Cookie <code>refreshToken</code> |
| **输出** | <code>Result&lt;TokenRefreshVO&gt;</code> + 新 Cookie |
| **目的** | 解析 Refresh JWT → 校验 Redis 会话 → 轮转 refreshToken → 新 accessToken |

#### A5 · 登出 POST /user/logout
| 项 | 说明 |
|----|------|
| **输入** | 网关透传 userId、sessionId（@LoginCheck） |
| **输出** | <code>Result&lt;Void&gt;</code> |
| **目的** | 销毁 Redis 会话、清 Cookie、记 LOGOUT 日志 |

### 模块 B · 用户资料

#### B1 · 当前用户 GET /user/me
| 项 | 说明 |
|----|------|
| **输入** | @LoginCheck，ThreadLocal userId |
| **输出** | Result<UserVO> |
| **目的** | 查本人完整资料含待审头像 |

#### B2 · 修改资料 PUT /user/info
| 项 | 说明 |
|----|------|
| **输入** | UpdateUserInfoDTO + version 乐观锁 |
| **输出** | Result<Void> |
| **目的** | 敏感字段 DFA+AI 异步审核，改手机号需验码 |

#### B3 · 上传头像 POST /user/avatar
| 项 | 说明 |
|----|------|
| **输入** | multipart avatar ≤2MB jpg/png/webp |
| **输出** | Result<String> 待审预览 URL |
| **目的** | 私有桶暂存 → AI 审核 → 通过后公共桶 |

#### B4 · 修改密码 PUT /user/password
| 项 | 说明 |
|----|------|
| **输入** | ChangePasswordDTO old/new |
| **输出** | Result<Void> |
| **目的** | BCrypt 更新密码 → invalidate 全端会话 |

### 模块 C · 用户查询

#### C1 · 按 accountId 查用户 GET /user/{accountId}
| 项 | 说明 |
|----|------|
| **输入** | path accountId |
| **输出** | Result<UserVO> |
| **目的** | 他人资料脱敏手机号；本人可见 pendingAvatar |

#### C2 · 简要信息 GET /user/simple/{accountId}
| 项 | 说明 |
|----|------|
| **输入** | path accountId |
| **输出** | Result<UserSimpleVO> |
| **目的** | 轻量用户信息 |

#### C3 · 批量查 GET /user/ids
| 项 | 说明 |
|----|------|
| **输入** | ids 内部 userId 列表 ≤100 |
| **输出** | Result<List<UserVO>> |
| **目的** | 批量组装 UserVO |

#### C4 · 用户名搜索 GET /user/simple/search
| 项 | 说明 |
|----|------|
| **输入** | page/size/username 前缀 |
| **输出** | PageResult<UserSimpleVO> |
| **目的** | 分页 like 搜索 |

#### C5 · Feign 账户状态 GET /feign/user/{userId}/account
| 项 | 说明 |
|----|------|
| **输入** | path userId |
| **输出** | Result<UserAccountVO> |
| **目的** | 跨服务查封禁/类型状态 |

### 模块 D · 账户生命周期

#### D1 · 申请注销 POST /user/cancel
| 项 | 说明 |
|----|------|
| **输入** | @LoginCheck 本人 |
| **输出** | Result<Void> |
| **目的** | status→CANCELLING，7 天冷静期 |

#### D2 · 撤销注销 POST /user/cancel/revoke
| 项 | 说明 |
|----|------|
| **输入** | @LoginCheck |
| **输出** | Result<Void> |
| **目的** | CANCELLING→NORMAL |

#### D3 · 封禁 POST /user/{userId}/ban
| 项 | 说明 |
|----|------|
| **输入** | @AdminCheck + BanUserDTO |
| **输出** | Result<Void> |
| **目的** | BANNED + invalidate 会话 |

#### D4 · 解封 POST /user/{userId}/unban
| 项 | 说明 |
|----|------|
| **输入** | @AdminCheck |
| **输出** | Result<Void> |
| **目的** | BANNED→NORMAL |

#### D5 · 定时任务 processExpiredBans
| 项 | 说明 |
|----|------|
| **输入** | AccountStatusScheduler 触发 |
| **输出** | void |
| **目的** | banUntil 过期自动解封 |

#### D6 · 定时任务 processExpiredCancellations
| 项 | 说明 |
|----|------|
| **输入** | AccountStatusScheduler 触发 |
| **输出** | void |
| **目的** | 冷静期结束完成注销、释放手机号 |

### 模块 F · Feign 内部接口

#### F1 · 批量用户 GET /feign/user/ids
| 项 | 说明 |
|----|------|
| **输入** | ids userId |
| **输出** | Result<List<UserVO>> |
| **目的** | 同 C3 无 LoginCheck |

#### F2 · Feign 封禁 POST /feign/user/{userId}/ban
| 项 | 说明 |
|----|------|
| **输入** | reason, durationHours query |
| **输出** | Result<Void> |
| **目的** | 审核服务调用封禁 |

#### F3 · Feign 解封 POST /feign/user/{userId}/unban
| 项 | 说明 |
|----|------|
| **输入** | path userId |
| **输出** | Result<Void> |
| **目的** | 内部解封 |
<!-- USER-SERVICE-AUTO -->

---

## 模块 E：内容服务（content-service）

### E1. 创建/发布文章 `POST /article`
| 项 | 说明 |
|----|------|
| **输入** | `ArticleDTO`：标题、摘要、分类、正文、图片、status（0 草稿 / 1 发布）、定时时间 |
| **输出** | `Result<Long>`（articleId） |
| **目的** | 草稿直写 Mongo；发布走审核任务链（DFA→AI 文本→AI 图片） |

### E2. 定时/立即任务调度
| 项 | 说明 |
|----|------|
| **输入** | 任务类型 + 业务 ID + 执行时间 |
| **输出** | taskId |
| **目的** | MySQL 持久化 + Redis 队列驱动异步审核发布 |

### E3. 文章搜索同步
| 项 | 说明 |
|----|------|
| **输入** | 文章 ID + 操作类型 |
| **输出** | Kafka 消息 |
| **目的** | 通知 search-service 更新 ES 索引 |


---

## 模块 F：社交服务（social-service）

### F1. 评论/回复/点赞
| 项 | 说明 |
|----|------|
| **输入** | 目标 ID、内容、分页参数 |
| **输出** | 评论/回复 VO、点赞结果 |
| **目的** | MySQL 元数据 + Mongo 评论正文；产出行为/通知 Kafka 事件 |

### F2. 关注/Feed
| 项 | 说明 |
|----|------|
| **输入** | 被关注 userId；Feed 游标 |
| **输出** | 关注关系；Feed 列表 |
| **目的** | 推模式写 `t_social_feed_item`；新粉丝回填最近 50 篇 |

### F3. 举报
| 项 | 说明 |
|----|------|
| **输入** | 目标类型、目标 ID、原因 |
| **输出** | 举报单 ID |
| **目的** | 写 MySQL → Kafka `report-audit-events` → audit-service |

---

---

## 模块 G：通知服务（notification-service）

### G1. 消费通知事件
| 项 | 说明 |
|----|------|
| **输入** | Kafka `notification-events` |
| **输出** | MySQL 通知记录 + SSE 推送 |
| **目的** | 持久化 + 实时提醒（点赞、评论、Feed 未读等） |

### G2. SSE 长连接 `GET /notification/sse/connect`
| 项 | 说明 |
|----|------|
| **输入** | 登录态 userId |
| **输出** | `text/event-stream` |
| **目的** | 浏览器实时收通知；25s 心跳保活 |

---

---

## 跨模块端到端场景

| 场景 | 涉及功能 | 最终目的 |
|------|----------|----------|
| 新用户注册登录 | A1→A2→A3→网关 | 完成身份建立与网关受保护访问 |
| 发布文章进 Feed | E1→E2→F2→G1 | 内容过审后粉丝可见并收到通知 |
| 点赞评论 | F1→G1 | 互动行为入库并实时通知作者 |
| 管理员封禁 | D3→A1 | 违规用户无法继续访问 |
