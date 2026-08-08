# 微服务代码规范（service 全模块）

本文件适用于 **`service/` 下所有微服务**（user、content、social、notification、shop、steam、search、recommend、ai-agent、audit 等）。日常开发与 Code Review **必须先读本文**；各服务若有领域补充，写在 `service/<name>/CODING_STANDARDS.md` 附录中。

与全局模块设计冲突时：本文 → 各服务 `docs/v2/*.md` → 服务附录。

---

## 1. 仓库模块边界

| 模块 | 放什么 | 不放什么 |
|------|--------|----------|
| **model** | `entity` / `dto` / `vo` / `payload` / `enums` / `mongo` | 业务逻辑、Spring 服务 |
| **common** | 跨服务常量、`@LoginCheck` / `@AdminCheck`、全局异常 | 任一服务的领域业务、Mapper |
| **feign** | 其他服务调用的 **Client 接口**（`@FeignClient`） | 实现类、HTTP Controller |
| **utils** | Redis/JWT/Cookie/邮件等基础设施 | 领域规则 |
| **service/\*** | 本服务 Controller、Service、Mapper、配置、领域组件 | 其他微服务业务 |

---

## 2. 单服务包结构（统一模板）

每个微服务根包为 `com.game.community.<domain>`（如 `user`、`content`、`social`）：

```
com.game.community.<domain>
├── controller/          # 对外 HTTP（路径见 §3，须带服务前缀）
├── feign/               # 服务间内部接口 /feign/<domain>/**
├── service/
│   └── impl/
├── mapper/              # MyBatis-Plus（无 DB 的服务可省略）
├── common/              # 本服务内 ≥2 处复用的领域能力（可选）
├── config/
├── filter/              # 网关 Header → ThreadLocal（按需）
├── aspect/              # 本服务切面（按需）
├── event/               # Kafka 等出站（按需）
└── runner/              # 启动任务（按需）
```

### 归属决策

| 写什么 | 放哪 |
|--------|------|
| HTTP 入口 | `controller/` 或 `feign/` |
| 业务流程、事务 | `service/impl/` |
| 表访问 | `mapper/` + `model.entity` |
| 仅本服务复用、无跨服务价值 | `common/` 或 `service/impl` 内私有方法 |
| 跨服务复用 | 提升到 `common` 或 `utils`（评审后） |
| Feign 消费方接口 | 仓库 `feign` 模块 |
| Feign 提供方实现 | 本服务 `feign/*Controller` |

**禁止**：Controller 写业务；Mapper 拼业务规则；为单行调用再套无意义 Facade。

---

## 3. API 路径规范（全服务强制）

### 3.1 总则

1. **对外 REST 必须以「服务域前缀」开头**，与网关路由、前端 `service/*.ts` 一致。
2. 同一服务内可按业务再加子路径：`/{服务前缀}/{业务段}/...`
3. **内部 Feign 接口**统一：`/feign/<domain>/**`，仅供微服务间调用，不对外暴露给前端。
4. **禁止**新接口使用无前缀的裸路径（如 `/login`、`/list`）；历史 `/api/shop/*` 等兼容路径不再新增。

### 3.2 各服务路径前缀（与 gateway 对齐）

| 服务 | Maven 模块 | 对外路径前缀 | 内部 Feign | 设计文档 |
|------|------------|--------------|------------|----------|
| user | `user-service` | `/user/**`（认证 `/user/auth/**`） | `/feign/user/**` | `docs/v2/user-service.md` |
| content | `content-service` | `/article/**`、`/category/**`、`/file/**`、`/share/post/**` | `/feign/content/**` | `docs/v2/content-service.md` |
| steam | `steam-service` | `/steam/**`、`/game/**` | `/feign/steam/**` | `docs/v2/steam-service-api-inventory.md` |
| social | `social-service` | `/social/**`、`/report/**` | `/feign/social/**` | `docs/v2/social-service.md` |
| notification | `notification-service` | `/notification/**` | — | `docs/v2/notification-service.md` |
| shop | `shop-service` | `/shop/**` | — | `docs/v2/shop-service.md` |
| search | `search-service` | `/search/**` | — | `docs/v2/search-service.md` |
| recommend | `recommend-service` | `/hot-article/**` | — | `docs/v2/recommend-service.md` |
| ai-agent | `ai-agent-service` | `/ai/**` | — | `docs/v2/ai-agent-service.md` |
| audit | `audit-service` | `/audit/**` | — | `docs/v2/audit-service.md` |

新增 Controller 时：**先查上表与前缀**，再注册网关 `Path`（若新前缀）与 `GatewayConstants` 白名单（若游客可读）。

### 3.3 Controller 写法

```java
@RestController
@RequestMapping("/social/comment")   // 完整路径 = 服务前缀 + 业务段
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @LoginCheck
    @PostMapping
    public Result<Long> create(@Valid @RequestBody CommentCreateDTO dto) {
        return commentService.create(dto);   // 只转发，不拼业务
    }
}
```

- 入参：`@Valid` + `model.dto.<domain>.*`
- 出参：**优先** `Result<T>` / `PageResult<T>` 由 **Service 构造** 并返回，Controller 原样 `return`
- 鉴权：`@LoginCheck` / `@AdminCheck`；取当前用户 ID 在 **Service** 内（`UserThreadLocal` 或本域 Helper），不在 Controller 堆逻辑
- 一个 Controller 方法对应 **一个** Service 方法

### 3.4 Feign 提供方

```java
@RestController
@RequestMapping("/feign/social")
@RequiredArgsConstructor
public class SocialFeignController {
    // 无 @LoginCheck：信任内网；由调用方服务鉴权
}
```

- 路径 **`/feign/<domain>/...`**，与对外 API **不要合并**
- 返回类型同样使用 `Result<T>`

### 3.5 网关

- 路由：`gateway/src/main/resources/application.yml`
- JWT 白名单 / 游客可读：`common/.../gateway/GatewayConstants.java`
- 认证公开接口：前缀 `/user/auth/`（见 `AUTH_PUBLIC_PATH_PREFIXES`）
- **改 API 路径必须同步**：本服务 Controller → gateway 路由 → 白名单 → 前端 `game-community/src/service/*.ts`

---

## 4. 分层与调用链

```
Controller → Service(接口) → ServiceImpl → Mapper / 本服务 common.*
                ↓
         model.dto → model.entity / enums
                ↓
         model.vo → Result.success("中文提示", vo)
```

### Service 约定

- 业务方法返回 `Result<T>` 或 `PageResult<T>`（新代码统一；旧代码逐步对齐）
- 写操作：`@Transactional(rollbackFor = Exception.class)`
- 抛错：`BusinessException` + `ApiErrorCodes` / 领域错误码 → `GlobalExceptionHandler`
- 禁止 Entity 直接作为 API 响应

### 4.1 抽象边界与对象映射

user-service 及其他 service 模块遵循“先保证业务流程直观，再抽取复用逻辑”的原则：

- **入口统一规范化一次**：请求进入 Service 后完成邮箱、编码、枚举等参数的规范化；后续内部方法接收已规范化的值，不重复调用 `normalize` 或重复做同一项校验。只有对外公开、可能被独立调用的边界方法，才自行承担输入规范化责任。
- **短转调优先内联**：少于 3 行、只调用另一个方法、没有增加业务语义的 private 方法直接内联；只有一行的 public 方法也要先确认它是不是 Controller/Feign/Bean 或明确的业务边界。因“按 accountId 转内部 userId”“当前用户入口与内部入口”“跨服务出站事件”等边界产生的适配方法，可以保留，但方法名必须说明边界含义。
- **简单字段映射优先批量复制**：源对象和目标对象字段同名、类型兼容且没有脱敏/默认值/业务转换时，使用 `BeanUtils.copyProperties`。涉及枚举转 code、敏感字段、字段改名、默认值或权限裁剪时，再显式赋值。
- **不要为了消除重复行制造通用框架**：三五行的直线业务直接写在当前方法中；只有被多处复用、包含分支/异常/事务边界、或有清晰领域语义的逻辑才抽成方法或组件。
- **优先保持线性调用链**：Controller → Service → Mapper。避免出现多层只做转发的 Facade/Helper；抽取后调用方应更容易理解业务流程，而不是需要连续跳转多个文件才能看懂。
- **重构以行为不变为前提**：删除辅助方法前先搜索全部调用方；规范化责任移动后同步调整接口注释和测试，不能只为减少代码行数而改变对外输入契约。

---

## 5. 数据模型

### 5.1 DTO / VO / Entity

| 类型 | 包路径 | 用途 |
|------|--------|------|
| 请求 | `model.dto.<domain>.*` | 接参、校验 |
| 响应 | `model.vo.<domain>.*` | 返回前端 / Feign |
| 持久化 | `model.entity.<domain>.*` | Mapper 读写 |
| 枚举 | `model.enums.<domain>.*` | 状态、类型，禁止魔法字符串/裸 int |

- **禁止**用 Entity 接 HTTP 参数或直接返回 Entity
- **禁止**维护「大一统 VO」兼容旧字段；按场景拆分 VO

### 5.2 枚举与常量

- 业务状态、类型 → **`model.enums.<domain>`**
- 跨服务错误码 → `common.constant.ApiErrorCodes` 等
- 服务内 **数值/时间配置** → `common.constant.<domain>.*Constants` 或 `application.yml`
- **禁止**在 `*Constants` 里堆业务状态字符串（参考 user 模块已清理的 `UserConstants`）

### 5.3 空值与默认值

- 字符串列 `NOT NULL DEFAULT ''` 的表：写入用空串，**避免 `null`**（user 域用 `UserStrings.EMPTY`；其他域可定义同类工具或 `""`）
- 时间字段：允许 `null` 表示「未设置」语义
- 新建服务 SQL：索引写在 `CREATE TABLE` 内，避免单独 `CREATE INDEX` 导致 `sync-mysql.sh` 重跑报重复（见 `sql/content.sql` 写法）

---

## 6. 鉴权与用户上下文

- 网关校验 JWT → 透传 `X-User-Id` 等 Header（`GatewayConstants`）
- 各服务 `filter` 写入 `UserThreadLocal`（user-service 已实现，其他服务按需）
- 需登录接口：`@LoginCheck`；管理接口：`@AdminCheck`
- 游客可读路径：在 `GatewayConstants.PUBLIC_READ_PATH_PREFIXES` 登记，**写操作仍须登录**

---

## 7. 数据库与迁移

- 全量/增量 SQL：`sql/*.sql`，顺序见 `scripts/db/migrations.order`
- 同步：`./scripts/db/sync-mysql.sh`（checksum 变更会重跑脚本）
- 改表：**同时**更新 `sql/<domain>.sql` 与对应服务 `src/test/resources/schema.sql`（若有集成测试）
- `user.sql` 类「先 DROP 再 CREATE」仅用于可重建的 dev 数据；其他脚本优先 `CREATE TABLE IF NOT EXISTS` + 表内索引

### 7.1 字符集与中文乱码（强制）

全链路统一 **UTF-8 / utf8mb4**，避免「库表是 utf8mb4、客户端却是 latin1」导致中文二次编码乱码（如 `综合讨论` 显示成 `åˆ›ä½œåˆ†äº«`）。

| 环节 | 要求 |
|------|------|
| **SQL 文件** | 保存为 **UTF-8（无 BOM）**；含中文的种子/注释脚本头部加 `SET NAMES utf8mb4;` |
| **sync-mysql.sh** | 所有 `mysql` 调用必须带 `--default-character-set=utf8mb4`（见 `mysql_exec` / `mysql_query`） |
| **表结构** | `DEFAULT CHARSET=utf8mb4` + `COLLATE=utf8mb4_unicode_ci`（或 `utf8mb4`） |
| **Docker MySQL** | `docker-compose.yml` 已配置 `--character-set-server=utf8mb4` |
| **JDBC** | `useUnicode=true&characterEncoding=UTF-8`（各服务 `application.yml` 已统一） |
| **HTTP API** | Spring Boot 默认 JSON UTF-8；**禁止**对响应体做错误 charset 转码 |
| **种子数据** | 需可覆盖乱码时：按业务键（如 `sort`）`DELETE` 后再 `INSERT`，不要只靠 `INSERT IGNORE` |

**手工执行 SQL 时**（排查/补数）：

```bash
docker exec -i mysql mysql --default-character-set=utf8mb4 \
  -uroot -proot123 game_community < sql/xxx.sql
```

**自检**：写入后抽查十六进制是否正确，例如 `综合讨论` → `E7BBBCE59088E8AE8AE8BAB2`：

```bash
docker exec mysql mysql --default-character-set=utf8mb4 -uroot -proot123 -D game_community \
  -e "SELECT name, HEX(name) FROM t_category WHERE name='综合讨论';"
```

---

## 8. 测试与交付

```bash
# 单服务（在仓库根目录）
mvn -pl service/<service-name> -am clean test

# 示例
mvn -pl service/user-service -am test
mvn -pl service/content-service -am test
```

- 集成测试：H2 / Testcontainers + `Abstract*IntegrationTest` 基类
- 异步/Kafka：用 `@MockBean` 避免测试依赖外部中间件
- 交付前：**本服务模块测试通过**再提 PR

---

## 9. 禁止事项（全服务）

- ❌ 对外 API 不带本服务域前缀（Feign `/feign/*` 除外）
- ❌ Controller 写业务或组装多个 Service 调用
- ❌ Entity 当 DTO/VO
- ❌ 业务魔法字符串 / 魔法数字（应用枚举）
- ❌ 无意义多层 delegate
- ❌ 改 API 不同步 gateway / 前端
- ❌ SQL 脚本不可重入（重复索引、重复列）
- ❌ 含中文的 SQL 不经 `utf8mb4` 客户端执行（`sync-mysql.sh` 已统一；手工补数须带 `--default-character-set=utf8mb4`）

---

## 10. 新增功能检查表

- [ ] 路径符合 §3.2 服务前缀 + 业务子路径
- [ ] Controller 只转发 Service；Service 返回 `Result`
- [ ] DTO/VO/枚举在 `model` 对应包下
- [ ] 写操作有事务；异步有明确线程池或消息
- [ ] 需登录加 `@LoginCheck`；游客可读则登记 gateway 白名单
- [ ] 新 Feign 提供方在 `feign/` 包，路径 `/feign/<domain>/...`
- [ ] 动表：更新 `sql/` + 测试 schema + `migrations.order`（新脚本）
- [ ] `mvn -pl service/<name> -am test` 通过

---

## 11. 各服务附录

| 服务 | 附录（领域细节） |
|------|------------------|
| user-service | [`user-service/CODING_STANDARDS.md`](user-service/CODING_STANDARDS.md) |
| content-service | [`content-service/CODING_STANDARDS.md`](content-service/CODING_STANDARDS.md) |

---

## 12. 相关文档

- 网关：`gateway/src/main/resources/application.yml`
- 网关常量：`common/.../gateway/GatewayConstants.java`
- 架构流：`docs/architecture/`
- 前端对接：`game-community` 仓 `src/service/*.ts`
- SQL 同步：`scripts/db/sync-mysql.sh`
