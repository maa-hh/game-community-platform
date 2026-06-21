# User-Service 后端 PRD

> 说明：本文是早期 PRD 草案，部分表结构和认证细节已经被后续实现升级。当前接口、表结构、Token 轮换、审核任务和 MinIO 双桶方案，以 `docs/user-module-api.md` 与 `docs/user-module-technical-overview.md` 为准。

## 一、目标

本阶段实现 `user-service` 的用户认证、登录态上下文、权限校验、用户资料维护、用户资料查询和注销能力。代码风格保持与 `demo` 基本一致：`controller -> service -> mapper` 分层，DTO/VO 放入 `model`，通用注解和常量放入 `common`，JWT、Redis、ThreadLocal、文件存储和审核工具放入 `utils`。

本阶段优先实现可测试、可扩展的核心闭环：

| 模块 | 功能 |
|------|------|
| 请求上下文 | `filter` 从 Header 读取用户信息并写入 ThreadLocal |
| 权限校验 | `@LoginCheck` 校验登录态，`@AdminCheck` 校验管理员权限 |
| 认证 | 账号密码登录、手机号验证码注册、登出 |
| 用户资料 | 用户信息修改、头像上传、注销账号 |
| 查询 | 用户简单信息、用户详细信息、当前登录用户信息 |
| 审核 | 昵称、签名、头像审核，优先设计为可切换审核提供方，支持接入 MINIMAX |
| 测试 | 复用根目录 `docker-compose.yml` 启动 MySQL、Redis、Nacos、MinIO |

## 二、边界

### 2.1 本阶段必须实现

1. `user-service` 独立启动并注册到 Nacos。
2. 登录成功后生成 JWT，将 token 写入 Redis。
3. Gateway 或测试请求携带 `X-User-Id`、`X-User-Type`、`X-Game-Account` 时，`UserFilter` 能写入 ThreadLocal。
4. `@LoginCheck`、`@AdminCheck` 可用于 Controller 方法权限校验。
5. 支持账号密码登录。
6. 支持手机号验证码注册。
7. 支持登出和账号注销。
8. 支持用户资料修改，并对昵称、签名、头像做审核。
9. 支持用户简单信息和详细信息查询。

### 2.2 本阶段暂不实现

1. 真实短信供应商发送验证码，开发环境先返回验证码并写 Redis。
2. 手机号验证码登录，可保留接口设计，后续按相同验证码逻辑扩展。
3. 复杂管理员后台列表、封禁审计流水，可在用户服务核心闭环完成后实现。
4. 头像异步审核队列，本阶段采用同步审核，后续可升级为 `pending -> pass/reject` 流程。

## 三、整体流程

### 3.1 登录流程

1. 前端调用 `POST /user/login/account`，提交账号 ID、密码、用户类型。
2. `UserController.loginByAccount` 接收 `AccountLoginDTO`。
3. `UserServiceImpl.loginByAccount` 查询用户，校验用户存在、密码正确、账号状态正常。
4. `buildLoginVO` 生成 JWT，写入 Redis：`user:token:{userId}`。
5. 返回 `LoginVO`，前端保存 token。
6. 后续请求由 Gateway 校验 token，并向下游服务转发用户 Header。

### 3.2 请求上下文流程

1. 请求进入 `user-service`。
2. `UserFilter` 读取 Header：
   | Header | 含义 |
   |--------|------|
   | `X-User-Id` | 用户 ID |
   | `X-User-Type` | 用户类型，0 普通用户，1 管理员 |
   | `X-Game-Account` | 游戏账号 |
3. 构造 `UserContex`。
4. 调用 `UserThreadLocal.setUser(userContex)`。
5. Controller、AOP、Service 内可通过 `UserThreadLocal.getUserId()` 获取当前用户。
6. 请求结束后 `finally` 调用 `UserThreadLocal.removeUser()`。

### 3.3 权限校验流程

1. Controller 方法标记 `@LoginCheck` 或 `@AdminCheck`。
2. `AuthAspect` 拦截注解方法。
3. `@LoginCheck` 判断 ThreadLocal 中是否存在 userId。
4. `@AdminCheck` 判断 userId 存在且 type 为管理员。
5. 校验失败返回 `Result.error(...)`，校验通过执行业务方法。

### 3.4 注册流程

1. 前端调用 `POST /user/sendCode` 获取验证码。
2. Redis 写入 `sms:code:{phone}`，有效期 5 分钟。
3. 前端调用 `POST /user/register/phone`。
4. 后端校验手机号、验证码、密码强度、昵称合规。
5. 查询手机号是否已注册。
6. 生成 salt，密码使用 `EncryptUtils.md5WithSalt(password, salt)` 存储。
7. 插入用户记录，默认普通用户、正常状态。
8. 返回用户 ID。

### 3.5 用户资料修改流程

1. 前端携带 token 调用 `PUT /user/info`。
2. `UserFilter` 写入当前用户上下文。
3. `@LoginCheck` 校验登录态。
4. `UserServiceImpl.updateUserInfo` 查询当前用户。
5. 前端必须提交 `version`。
6. 手机号变更时校验格式和唯一性。
7. 先用 `version + audit_status` 乐观锁抢占用户资料审核状态。
8. 新资料和 `userVersion` 写入审核任务表。
9. 事务提交后异步审核，审核通过时按 `userVersion` 再次 CAS 回写正式资料。

### 3.6 注销流程

1. 前端调用 `PUT /user/status` 或建议新增 `POST /user/cancel`。
2. 后端只允许当前用户注销自己。
3. 用户状态改为 `1`，表示禁用或注销。
4. 删除 Redis 中 `user:token:{userId}`。
5. 后续请求因 token 失效或状态异常无法继续访问。
6. 当前实现实际删除的是该用户全部 Session 与 active-session 指针，不再是单个旧 token Key。

## 四、包结构设计

### 4.1 `common`

```
common/
└── src/main/java/com/game/community/common/
    ├── annotation/
    │   ├── LoginCheck.java
    │   └── AdminCheck.java
    ├── constant/
    │   ├── Constants.java
    │   ├── gateway/GatewayConstants.java
    │   └── user/
    │       ├── RedisConstants.java
    │       └── UserConstants.java
    └── exception/
        ├── BusinessException.java
        └── GlobalExceptionHandler.java
```

#### `LoginCheck`

| 项 | 内容 |
|----|------|
| 包 | `com.game.community.common.annotation` |
| 类型 | 方法注解 |
| 作用 | 标记需要登录态的 Controller 方法 |
| 实现 | `@Target(ElementType.METHOD)`、`@Retention(RetentionPolicy.RUNTIME)` |

#### `AdminCheck`

| 项 | 内容 |
|----|------|
| 包 | `com.game.community.common.annotation` |
| 类型 | 方法注解 |
| 作用 | 标记需要管理员权限的 Controller 方法 |
| 实现 | 同 `LoginCheck` |

#### `GatewayConstants`

| 常量 | 值 | 说明 |
|------|----|------|
| `USER_ID_HEADER` | `X-User-Id` | 网关向服务透传用户 ID |
| `USER_TYPE_HEADER` | `X-User-Type` | 网关向服务透传用户类型 |
| `GAME_ACCOUNT_HEADER` | `X-Game-Account` | 网关向服务透传游戏账号 |

#### `RedisConstants`

| 常量 | 值 | 说明 |
|------|----|------|
| `CODE_PREFIX` | `sms:code:` | 手机验证码 |
| `TOKEN_PREFIX` | `user:token:` | 登录 token |
| `REGISTER_LOCK_PREFIX` | `user:register:lock:` | 防并发注册锁，建议新增 |

#### `UserConstants`

需要统一 `demo` 中状态值含义，建议采用下面定义：

| 常量 | 值 | 说明 |
|------|----|------|
| `UserStatus.NORMAL` | `0` | 正常 |
| `UserStatus.DISABLED` | `1` | 禁用或注销 |
| `UserType.NORMAL` | `0` | 普通用户 |
| `UserType.ADMIN` | `1` | 管理员 |

## 五、`model` 设计

### 5.1 实体包

```
model/
└── src/main/java/com/game/community/model/
    ├── entity/user/User.java
    ├── dto/user/*.java
    ├── vo/user/*.java
    └── ThreadLocal/UserContex.java
```

### 5.2 `User` 实体

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `Long` | 主键，账号 ID |
| `username` | `String` | 昵称或用户名，前端展示名 |
| `password` | `String` | 加盐后密码 |
| `salt` | `String` | 密码盐值 |
| `avatar` | `String` | 头像 URL |
| `signature` | `String` | 个性签名 |
| `phone` | `String` | 手机号，唯一 |
| `status` | `Integer` | `0` 正常，`1` 禁用或注销 |
| `type` | `Integer` | `0` 普通用户，`1` 管理员 |
| `gameAccount` | `String` | 绑定游戏账号 |
| `version` | `Integer` | 乐观锁版本号 |
| `createTime` | `LocalDateTime` | 创建时间 |
| `updateTime` | `LocalDateTime` | 更新时间 |
| `deleted` | `Integer` | 逻辑删除 |

### 5.3 表设计优化

建议表名保持 `t_user`，方便和 `demo` 迁移一致。

```sql
CREATE TABLE IF NOT EXISTS t_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
  account_id BIGINT DEFAULT NULL COMMENT '公开账号ID',
  username VARCHAR(32) NOT NULL COMMENT '昵称',
  avatar VARCHAR(512) DEFAULT NULL COMMENT '头像URL',
  signature VARCHAR(120) DEFAULT NULL COMMENT '个性签名',
  phone VARCHAR(20) NOT NULL COMMENT '手机号',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0正常 1禁用/注销',
  type TINYINT NOT NULL DEFAULT 0 COMMENT '0普通用户 1管理员',
  game_account VARCHAR(64) DEFAULT NULL COMMENT '游戏账号',
  audit_status TINYINT NOT NULL DEFAULT 0 COMMENT '0空闲 1审核中',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  last_login_time DATETIME DEFAULT NULL COMMENT '最后登录时间',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  UNIQUE KEY uk_phone_deleted (phone, deleted),
  UNIQUE KEY uk_account_id (account_id),
  KEY idx_username (username),
  KEY idx_status_type (status, type),
  KEY idx_game_account (game_account)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

```sql
CREATE TABLE IF NOT EXISTS t_user_auth (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  password VARCHAR(64) NOT NULL COMMENT '加盐密码',
  salt VARCHAR(32) NOT NULL COMMENT '密码盐值',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户认证表';
```

优化点：

| 点 | 说明 |
|----|------|
| 手机号唯一 | 使用 `uk_phone_deleted`，兼顾逻辑删除 |
| 公开账号号 | `account_id` 在同表维护，注册时由 `id + 9999` 回填 |
| 乐观锁 | `version` 保护资料写入和审核结果回写 |
| 安全拆表 | 密码和盐迁移到 `t_user_auth`，并独立维护 `version` |
| 查询索引 | 用户名前缀查询、状态筛选、游戏账号查询各自有索引 |
| 状态统一 | 采用 `0` 正常、`1` 禁用，和 `demo` 当前实现一致 |
| 登录时间 | 增加 `last_login_time`，便于后台和风控 |

### 5.4 DTO

#### `SendCodeDTO`

| 字段 | 类型 | 校验 | 说明 |
|------|------|------|------|
| `phone` | `String` | `@NotBlank`、手机号格式 | 接收验证码手机号 |

#### `AccountLoginDTO`

| 字段 | 类型 | 校验 | 说明 |
|------|------|------|------|
| `accountId` | `Long` | `@NotNull` | 账号 ID |
| `password` | `String` | `@NotBlank` | 密码 |
| `type` | `Integer` | 默认 0 | 登录用户类型 |

#### `RegisterDTO`

| 字段 | 类型 | 校验 | 说明 |
|------|------|------|------|
| `username` | `String` | `@NotBlank`、长度 2-32 | 昵称 |
| `password` | `String` | `@NotBlank`、长度 6-32 | 密码 |
| `phone` | `String` | `@NotBlank`、手机号格式 | 手机号 |
| `code` | `String` | `@NotBlank`、6 位数字 | 验证码 |
| `gameAccount` | `String` | 可空 | 游戏账号 |

#### `UpdateUserInfoDTO`

| 字段 | 类型 | 说明 |
|------|------|------|
| `version` | `Integer` | 当前资料版本号，必填 |
| `username` | `String` | 昵称 |
| `signature` | `String` | 个性签名 |
| `phone` | `String` | 手机号 |
| `gameAccount` | `String` | 游戏账号 |

#### `UserStatusDTO`

| 字段 | 类型 | 说明 |
|------|------|------|
| `status` | `Integer` | `0` 启用，`1` 注销或禁用 |

### 5.5 VO

#### `LoginVO`

| 字段 | 类型 | 说明 |
|------|------|------|
| `token` | `String` | JWT |
| `userId` | `Long` | 用户 ID |
| `username` | `String` | 昵称 |
| `avatar` | `String` | 头像 |
| `type` | `Integer` | 用户类型 |
| `gameAccount` | `String` | 游戏账号 |

#### `UserSimpleVO`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `Long` | 用户 ID |
| `username` | `String` | 昵称 |
| `avatar` | `String` | 头像 |
| `gameAccount` | `String` | 游戏账号 |

#### `UserVO`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `Long` | 用户 ID |
| `accountId` | `Long` | 公开账号 ID |
| `username` | `String` | 昵称 |
| `avatar` | `String` | 头像 |
| `signature` | `String` | 个性签名 |
| `phone` | `String` | 手机号，只有本人或管理员可见 |
| `status` | `Integer` | 用户状态 |
| `type` | `Integer` | 用户类型 |
| `gameAccount` | `String` | 游戏账号 |
| `auditStatus` | `Integer` | 审核状态 |
| `version` | `Integer` | 当前资料版本号 |
| `followCount` | `Integer` | 关注数，后续可调用 social-service |
| `fansCount` | `Integer` | 粉丝数，后续可调用 social-service |

## 六、`utils` 设计

### 6.1 `UserThreadLocal`

| 方法 | 入参 | 返回 | 说明 |
|------|------|------|------|
| `setUser(UserContex user)` | 用户上下文 | `void` | 写入当前请求用户 |
| `getUser()` | 无 | `UserContex` | 建议新增，避免空指针 |
| `getUserId()` | 无 | `Long` | 获取用户 ID，无登录返回 `null` |
| `getType()` | 无 | `Integer` | 获取用户类型，无登录返回 `null` |
| `getGameAccount()` | 无 | `String` | 获取游戏账号 |
| `removeUser()` | 无 | `void` | 清理当前请求用户 |

内部逻辑：

1. 使用 `private static final ThreadLocal<UserContex> USER_CONTEXT = new ThreadLocal<>();`。
2. `getUserId()` 先取 `UserContex context = USER_CONTEXT.get()`。
3. context 为 `null` 时返回 `null`，避免 AOP 校验时空指针。
4. Filter 的 `finally` 中必须调用 `removeUser()`。

### 6.2 `JwtUtils`

沿用 `demo` 的 JWT 工具。

| 方法 | 说明 |
|------|------|
| `generateToken(secret, claims, expire)` | 登录成功生成 token |
| `parseToken(secret, token)` | Gateway 校验 token 时使用 |

### 6.3 `EncryptUtils`

沿用 `demo` 的加盐 MD5 风格。

| 方法 | 说明 |
|------|------|
| `generateSalt()` | 生成密码盐 |
| `md5WithSalt(rawPassword, salt)` | 注册或改密时加密 |
| `md5Check(rawPassword, encodedPassword, salt)` | 登录时校验 |

## 七、`user-service` 包设计

```
service/user-service/
└── src/main/java/com/game/community/user/
    ├── UserServiceApplication.java
    ├── aspect/AuthAspect.java
    ├── controller/UserController.java
    ├── filter/UserFilter.java
    ├── mapper/UserMapper.java
    └── service/
        ├── IUserService.java
        ├── UserAuditService.java
        └── impl/
            ├── UserServiceImpl.java
            └── UserAuditServiceImpl.java
```

### 7.1 `UserServiceApplication`

职责：

1. Spring Boot 启动类。
2. 开启 Mapper 扫描：`@MapperScan("com.game.community.user.mapper")`。
3. 开启 Feign：`@EnableFeignClients(basePackages = "com.game.community.feign")`，后续详细信息需要 social-service 时使用。

### 7.2 `filter/UserFilter`

职责：

1. 从 Header 中获取登录用户信息。
2. 写入 ThreadLocal。
3. 请求结束清理 ThreadLocal。

关键函数：

| 函数 | 说明 |
|------|------|
| `doFilter(ServletRequest request, ServletResponse response, FilterChain chain)` | 读取 Header、设置上下文、转发 Controller、清理上下文 |

内部逻辑：

1. 将 `ServletRequest` 转为 `HttpServletRequest`。
2. 读取 `GatewayConstants.USER_ID_HEADER`。
3. userId 存在时解析 `Long`。
4. 读取 `GatewayConstants.USER_TYPE_HEADER`，为空时默认普通用户。
5. 读取 `GatewayConstants.GAME_ACCOUNT_HEADER`，为空时默认空字符串。
6. 构造 `UserContex(userId, userType, gameAccount)`。
7. 调用 `UserThreadLocal.setUser`。
8. `chain.doFilter` 转发到 Controller。
9. `finally` 中调用 `UserThreadLocal.removeUser`。

异常策略：

| 场景 | 策略 |
|------|------|
| Header 缺失 | 不写 ThreadLocal，交给 AOP 判断 |
| userId 解析失败 | 打 warn 日志，不写 ThreadLocal |
| Controller 抛异常 | 不吞异常，继续交给全局异常处理 |

### 7.3 `aspect/AuthAspect`

职责：

1. 拦截登录校验。
2. 拦截管理员权限校验。

关键函数：

| 函数 | 说明 |
|------|------|
| `aroundLoginCheck(ProceedingJoinPoint joinPoint)` | 校验 ThreadLocal 中 userId 是否存在 |
| `aroundAdminCheck(ProceedingJoinPoint joinPoint)` | 校验 userId 和用户类型 |

内部逻辑：

`aroundLoginCheck`：

1. 调用 `UserThreadLocal.getUserId()`。
2. userId 为空返回 `Result.error("请先登录")`。
3. 不为空执行 `joinPoint.proceed()`。

`aroundAdminCheck`：

1. 调用 `UserThreadLocal.getUserId()`。
2. userId 为空返回 `Result.error("请先登录")`。
3. 调用 `UserThreadLocal.getType()`。
4. type 不等于 `UserConstants.UserType.ADMIN` 返回 `Result.error("无权限，需要管理员权限")`。
5. 校验通过执行业务方法。

### 7.4 `controller/UserController`

接口统一前缀：`/user`。

#### 认证接口

| 方法 | 路径 | 权限 | 入参 | 返回 | 说明 |
|------|------|------|------|------|------|
| `POST` | `/sendCode` | 无 | `SendCodeDTO` | `Result<String>` | 发送验证码，开发环境返回验证码 |
| `POST` | `/register/phone` | 无 | `RegisterDTO` | `Result<Long>` | 手机号验证码注册 |
| `POST` | `/login/account` | 无 | `AccountLoginDTO` | `Result<LoginVO>` | 账号密码登录 |
| `POST` | `/logout` | `@LoginCheck` | 无 | `Result<Void>` | 登出 |

#### 用户资料接口

| 方法 | 路径 | 权限 | 入参 | 返回 | 说明 |
|------|------|------|------|------|------|
| `GET` | `/me` | `@LoginCheck` | 无 | `Result<UserVO>` | 查询当前登录用户详细信息 |
| `PUT` | `/info` | `@LoginCheck` | `UpdateUserInfoDTO` | `Result<Void>` | 修改用户资料 |
| `POST` | `/avatar` | `@LoginCheck` | `MultipartFile avatar` | `Result<String>` | 上传头像并审核 |
| `POST` | `/cancel` | `@LoginCheck` | 无 | `Result<Void>` | 注销当前账号 |
| `PUT` | `/status` | `@LoginCheck` | `UserStatusDTO` | `Result<Void>` | 兼容 demo 的账号状态切换 |

#### 查询接口

| 方法 | 路径 | 权限 | 入参 | 返回 | 说明 |
|------|------|------|------|------|------|
| `GET` | `/{id}` | `@LoginCheck` | `Long id` | `Result<UserVO>` | 查询用户详细信息 |
| `GET` | `/simple/{id}` | 可无登录或 `@LoginCheck` | `Long id` | `Result<UserSimpleVO>` | 查询用户简单信息 |
| `GET` | `/ids` | 内部调用 | `List<Long> ids` | `Result<List<UserVO>>` | 批量查询用户 |
| `GET` | `/simple/search` | `@LoginCheck` | `UserSearchPageDTO` | `PageResult<UserSimpleVO>` | 用户名前缀搜索 |

### 7.5 `service/IUserService`

| 函数 | 入参 | 返回 | 说明 |
|------|------|------|------|
| `String sendCode(String phone)` | 手机号 | 验证码 | 生成验证码并写 Redis |
| `Long registerByPhone(RegisterDTO dto)` | 注册 DTO | 用户 ID | 验证码注册 |
| `LoginVO loginByAccount(AccountLoginDTO dto)` | 登录 DTO | 登录信息 | 账号密码登录 |
| `void logout(Long userId)` | 用户 ID | 无 | 删除 Redis token |
| `UserVO getCurrentUser(Long userId)` | 用户 ID | 详细信息 | 当前用户详情 |
| `UserVO getUserVOById(Long id)` | 用户 ID | 详细信息 | 查询详细用户信息 |
| `UserSimpleVO getUserSimpleById(Long id)` | 用户 ID | 简单信息 | 查询简单用户信息 |
| `List<UserVO> getUsersByIds(List<Long> ids)` | 用户 ID 集合 | 用户列表 | 内部批量查询 |
| `void updateUserInfo(Long userId, UpdateUserInfoDTO dto)` | 用户 ID、资料 DTO | 无 | 修改资料 |
| `String uploadAvatar(Long userId, MultipartFile avatarFile)` | 用户 ID、文件 | 头像 URL | 上传并审核 |
| `void cancelAccount(Long userId)` | 用户 ID | 无 | 注销当前用户 |
| `void switchUserStatus(Long userId, Integer status)` | 用户 ID、状态 | 无 | 兼容状态切换 |

### 7.6 `service/impl/UserServiceImpl`

#### `sendCode`

内部逻辑：

1. 校验手机号格式。
2. 生成 6 位随机数字。
3. 写 Redis：`sms:code:{phone}`。
4. 过期时间使用 `UserConstants.CODE_EXPIRE`。
5. 开发环境返回验证码，生产环境接短信供应商后返回固定文案。

#### `registerByPhone`

内部逻辑：

1. 从 Redis 获取 `sms:code:{phone}`。
2. 验证码不存在则抛出 `BusinessException("验证码已过期")`。
3. 验证码不一致则抛出 `BusinessException("验证码错误")`。
4. 删除验证码，避免重复使用。
5. 查询手机号是否存在。
6. 调用 `UserAuditService.auditUserInfo(username, null)` 审核昵称。
7. 生成 salt 和加密密码。
8. 创建 `User`：`status=0`、`type=0`、`avatar=null`、`signature=null`。
9. 保存用户。
10. 返回用户 ID。

并发策略：

1. 注册前尝试写 Redis 锁：`user:register:lock:{phone}`。
2. 获取锁失败返回“操作过于频繁”。
3. 数据库唯一索引兜底手机号重复。

#### `loginByAccount`

内部逻辑：

1. 根据 `accountId` 和 `type` 查询用户。
2. 用户不存在，抛出“账号不存在”。
3. 使用 `EncryptUtils.md5Check` 校验密码。
4. 密码错误，抛出“账号或密码错误”。
5. `status == 1`，抛出“账号已被禁用”。
6. 尝试获取 `user:login:lock:{userId}`，串行化同账号登录。
7. 删掉旧 Session，写入新的 `user:session:{sessionId}` 和 `user:active-session:{userId}`。
8. 更新 `last_login_time`。
9. 调用 `buildLoginVO(user)`。

#### `buildLoginVO`

内部逻辑：

1. claims 写入：`userId`、`type`、`gameAccount`。
2. 后端生成 `sessionId` 并写入 access/refresh token claims。
3. 使用不同 secret 生成 access token 和 refresh token。
4. Redis 写入 `user:session:{sessionId}`，保存 refresh token 哈希。
5. Redis 写入 `user:active-session:{userId}`，指向当前有效会话。
6. 组装 `LoginVO`。
7. 返回 access token、用户 ID、公开账号 ID、昵称、头像、类型、游戏账号。

#### `logout`

内部逻辑：

1. 删除 Redis：`user:token:{userId}`。
2. 删除当前 `sessionId` 对应的 `user:session:{sessionId}`。
3. 如果 `active-session` 仍指向该 `sessionId`，删除 `user:active-session:{userId}`。
4. 清理 ThreadLocal 可由 Controller 或 Filter 结束时完成。
5. 返回成功。

#### `updateUserInfo`

内部逻辑：

1. 查询当前用户是否存在。
2. 校验账号状态是否正常。
3. 检查 `dto.version` 是否等于当前 `user.version`，否则返回“资料已更新，请刷新页面后重试”。
4. 昵称不为空时校验长度 2-32，并走审核。
5. 签名不为空时校验长度不超过 120，并走审核。
6. 手机号不为空时校验格式和唯一性。
7. 游戏账号不为空时校验长度和格式。
8. 使用 `WHERE id = ? AND version = ? AND audit_status = 0` 的 CAS 抢占审核状态，并把 `version` 自增。
9. 新资料写入任务表，任务里带上 `userVersion`。
10. 事务提交后再投递异步审核。

#### `uploadAvatar`

内部逻辑：

1. 查询当前用户是否存在。
2. 校验文件非空、大小、类型。
3. 使用 `WHERE id = ? AND version = ? AND audit_status = 0` 的 CAS 抢占审核状态，并把 `version` 自增。
4. 上传到 MinIO 私有桶，返回临时预览地址。
5. 新头像对象名和 `userVersion` 写入任务表。
6. 事务提交后异步审核。
7. 审核通过时复制到公共桶，并按 `userVersion` CAS 更新正式头像。
8. 尝试删除旧头像和私有待审对象，失败只记录日志。
9. 返回临时预览 URL。

#### `cancelAccount`

内部逻辑：

1. 查询当前用户是否存在。
2. 状态已经禁用则直接返回成功。
3. 更新状态为 `1`。
4. 删除 Redis token。
5. 后续可扩展发布用户注销事件给其他服务清理缓存。

#### `getUserVOById`

内部逻辑：

1. 查询用户。
2. 不存在抛出“用户不存在”。
3. `status != 0` 时，非本人和非管理员只能看到受限信息或抛出“用户不存在”。
4. 如果当前登录用户存在，后续可调用 social-service 判断黑名单。
5. 转为 `UserVO`。
6. 手机号仅本人或管理员可见，其他用户置空。

#### `getUserSimpleById`

内部逻辑：

1. 查询用户。
2. 用户不存在或状态异常，抛出“用户不存在”。
3. 返回 `id`、`username`、`avatar`、`gameAccount`。

### 7.7 `service/UserAuditService`

| 函数 | 入参 | 返回 | 说明 |
|------|------|------|------|
| `boolean auditUserInfo(String nickname, String signature)` | 昵称、签名 | 是否通过 | 文本审核 |
| `boolean auditAvatar(String avatarUrl)` | 头像 URL | 是否通过 | 图片审核 |

### 7.8 审核方案

建议将审核能力抽象为“可切换提供方”，避免把 MINIMAX 或阿里云写死在业务逻辑里。

配置：

```yaml
audit:
  provider: minimax
  fail-open: false
  minimax:
    api-key: ${MINIMAX_API_KEY:}
    endpoint: https://api.minimax.chat/v1/text/chatcompletion_v2
```

审核策略：

| 类型 | 推荐流程 |
|------|----------|
| 昵称 | 本地 DFA 词库 -> MINIMAX 文本审核 |
| 个性签名 | 本地 DFA 词库 -> MINIMAX 文本审核 |
| 头像 | 图片基础校验 -> MinIO 上传 -> MINIMAX 多模态审核或 OCR 文本审核 |

MINIMAX 接入建议：

1. 新增 `utils.audit.AuditClient` 接口。
2. 新增 `utils.audit.MiniMaxAuditClient` 实现。
3. `UserAuditServiceImpl` 只依赖 `AuditClient`，不关心具体供应商。
4. `audit.fail-open=false` 时，审核接口异常直接拒绝修改；开发环境可以设为 `true`。
5. 保存审核日志字段建议后续加表：`t_audit_log`，记录 userId、业务类型、内容摘要、结果、原因、供应商。

MINIMAX 文本审核提示词建议：

```text
你是游戏社区的内容安全审核器。请判断用户昵称或个性签名是否包含违法违规、辱骂、色情、政治敏感、广告引流、人身攻击、未成年人不适宜内容。只返回 JSON：
{"pass":true,"reason":"通过"}
或
{"pass":false,"reason":"具体原因"}
```

## 八、Mapper 设计

### `UserMapper`

继承：

```java
public interface UserMapper extends BaseMapper<User> {
}
```

自定义函数：

| 函数 | 入参 | 返回 | 说明 |
|------|------|------|------|
| `int checkPhoneExists(String phone, Long excludeUserId)` | 手机号、排除用户 ID | 数量 | 修改手机号时校验唯一 |
| `int updateUserInfoByMap(Map<String, Object> params)` | 动态字段 | 影响行数 | 局部更新用户资料 |

如果使用 MyBatis-Plus `UpdateWrapper` 能覆盖局部更新场景，可以不写 XML，保持更简单。

## 九、接口详情

### 9.1 发送验证码

`POST /user/sendCode`

请求：

```json
{
  "phone": "13800138000"
}
```

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": "123456"
}
```

### 9.2 手机号验证码注册

`POST /user/register/phone`

请求：

```json
{
  "username": "玩家小明",
  "password": "123456",
  "phone": "13800138000",
  "code": "123456",
  "gameAccount": "game_10001"
}
```

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": 10001
}
```

### 9.3 账号密码登录

`POST /user/login/account`

请求：

```json
{
  "accountId": 10001,
  "password": "123456",
  "type": 0
}
```

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "jwt-token",
    "userId": 10001,
    "username": "玩家小明",
    "avatar": "http://localhost:9000/game-community/avatar.png",
    "type": 0,
    "gameAccount": "game_10001"
  }
}
```

### 9.4 登出

`POST /user/logout`

Header：

| Header | 示例 |
|--------|------|
| `Authorization` | `Bearer jwt-token` |
| `X-User-Id` | `10001` |
| `X-User-Type` | `0` |

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

### 9.5 修改用户信息

`PUT /user/info`

请求：

```json
{
  "username": "新的昵称",
  "signature": "今天也想赢一局",
  "phone": "13800138001",
  "gameAccount": "game_10001"
}
```

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

### 9.6 上传头像

`POST /user/avatar`

请求：

| 类型 | 字段 |
|------|------|
| `multipart/form-data` | `avatar` |

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": "http://localhost:9000/game-community/user-files/avatar.png"
}
```

### 9.7 当前用户详情

`GET /user/me`

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 10001,
    "username": "玩家小明",
    "avatar": "avatar-url",
    "signature": "今天也想赢一局",
    "phone": "13800138000",
    "status": 0,
    "type": 0,
    "gameAccount": "game_10001",
    "followCount": 0,
    "fansCount": 0
  }
}
```

### 9.8 用户简单信息

`GET /user/simple/{id}`

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 10001,
    "username": "玩家小明",
    "avatar": "avatar-url",
    "gameAccount": "game_10001"
  }
}
```

### 9.9 用户详细信息

`GET /user/{id}`

响应和 `/user/me` 类似，但非本人访问时 `phone` 置空。

### 9.10 注销账号

`POST /user/cancel`

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

## 十、测试方案

### 10.1 Docker 环境

根目录已复用 `demo` 的 `docker-compose.yml`。

启动：

```bash
docker compose up -d mysql redis nacos minio
```

必要服务：

| 服务 | 地址 |
|------|------|
| MySQL | `localhost:3307` |
| Redis | `localhost:6380` |
| Nacos | `localhost:8848` |
| MinIO API | `localhost:9000` |
| MinIO 控制台 | `localhost:9001` |

### 10.2 单元测试

建议在 `test` 模块或 `user-service/src/test/java` 增加：

| 测试类 | 覆盖 |
|--------|------|
| `UserServiceRegisterTest` | 验证码注册、重复手机号、验证码错误 |
| `UserServiceLoginTest` | 账号密码登录、密码错误、禁用账号 |
| `UserServiceUpdateTest` | 修改昵称、签名、手机号唯一性 |
| `AuthAspectTest` | 未登录、普通用户、管理员权限 |
| `UserFilterTest` | Header 写入 ThreadLocal 和清理 |

### 10.3 接口测试顺序

1. `docker compose up -d mysql redis nacos minio`
2. 执行 `sql/user.sql` 初始化用户表。
3. 启动 `user-service`。
4. 调用 `POST /user/sendCode`。
5. 调用 `POST /user/register/phone`。
6. 调用 `POST /user/login/account`。
7. 使用返回 token 和 Header 调用 `GET /user/me`。
8. 调用 `PUT /user/info`。
9. 调用 `POST /user/logout`。
10. 再次访问登录接口验证 token 已清理。

## 十一、验收标准

| 编号 | 标准 |
|------|------|
| BE-01 | `mvn validate` 通过 |
| BE-02 | `user-service` 能独立启动 |
| BE-03 | 验证码能写入 Redis 并按时过期 |
| BE-04 | 手机号验证码注册成功后 MySQL 有用户数据 |
| BE-05 | 账号密码登录成功后 Redis 有 token |
| BE-06 | Header 中的用户信息能写入 ThreadLocal |
| BE-07 | 未登录访问 `@LoginCheck` 接口返回“请先登录” |
| BE-08 | 普通用户访问 `@AdminCheck` 接口返回“无权限” |
| BE-09 | 用户资料修改会触发文本审核 |
| BE-10 | 注销账号后 token 被删除，账号不可继续登录 |
