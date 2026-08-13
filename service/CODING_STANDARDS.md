# 微服务代码规范（service 全模块）

本文件适用于 **`service/` 下所有微服务**（user、content、social、notification、shop、steam、search、recommend、ai-agent、audit 等）。日常开发与 Code Review **必须先读本文**；各服务若有领域补充，写在 `service/<name>/CODING_STANDARDS.md` 附录中。

与全局模块设计冲突时：本文 → 各服务 `docs/v2/*.md` → 服务附录。

## 0. 规则生效与编写前置检查（强制）

本文件是 `service/` 后端代码的默认约束，也是 AI 助手、Code Review 和日常开发的共同依据。任何新增、修改或重构后端代码，必须按以下顺序执行：

1. 先阅读本文件及目标服务附录；涉及 DTO/VO、接口协议、数据库或 Feign 时，同时阅读对应设计文档。
2. 先确认代码所属边界，再修改；不得为了“方便调用”把业务逻辑、内部主键或中间件对象跨层传递。
3. 完成修改后用 `rg` 搜索旧类名、旧方法名、旧配置键和旧协议字段，清理死代码与残留调用。
4. 运行对应服务测试；涉及公共模块、Feign、路由、配置或服务间协议时，至少运行全仓编译。
5. 若现有代码与本规范冲突，新增代码按本规范执行；兼容旧协议时必须保留清晰的边界适配，并在代码或文档中说明迁移原因。

后续规则更新也必须遵循同一流程：规则变更应与代码变更同时提交，不能只改实现而不补充规范。

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

#### 3.3.1 复杂入参与出参

- 一个接口同时包含路径参数、分页参数、筛选条件、游标或多个业务字段时，必须定义 `model.dto.<domain>` 下的请求 DTO；不要在 Controller 中堆叠多个 `@RequestParam`。
- 分页、筛选、批量查询、服务间命令等请求使用有业务含义的 DTO 名称，例如 `FeedQueryDTO`、`ReportPageQueryDTO`、`PublishArticleFeedDTO`，禁止使用无语义的 `Map`、`Object[]` 或参数列表代替。
- Controller 只负责绑定、鉴权注解和一次性转发；publicId 到内部主键的解析、游标解析、批量映射、默认值归一化和异常转换放在 Service 边界。
- 出参复杂时按场景拆分 VO；不要为了复用建立包含内部字段、管理字段和公开字段的“大一统 VO”。

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
- 服务间命令参数超过两个或包含时间、状态等复合字段时，使用 `model.dto` 命令对象作为 `@RequestBody`；不要把内部主键、时间解析和内部凭证校验散落在 Controller 参数中。
- 内部凭证、签名和请求头校验放在 `filter` / `interceptor` 等基础设施边界；Controller 不接收或校验中间件凭证。

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

#### 4.1.1 函数拆分判定

新增或修改函数时，按下面顺序判断是否需要抽取：

1. **先保证主流程可读**：业务只有几行、执行顺序直线、没有独立错误处理或事务边界时，直接写在当前函数中。
2. **少于 3 行谨慎抽取**：少于 3 行且只是调用另一个函数、返回另一个函数结果或简单赋值的函数，默认内联；除非它是 Controller/Feign/Bean 暴露边界，或方法名明确表达了权限、幂等、账号 ID 映射等业务语义。
3. **满足任一条件才抽取**：逻辑在两个以上调用点复用；包含完整的校验/异常处理；有独立事务、锁、外部 IO 或资源清理边界；分支本身有清晰领域含义；抽取后能显著降低主流程复杂度。
4. **抽取后不能制造跳转链**：调用方跳转两个以上类仍只能看到转发代码，说明抽取位置不合理，应合并回业务函数或把真正的业务逻辑下沉到一个明确的 Service/组件。
5. **函数名必须说明业务动作**：避免 `process`、`handle`、`doSomething`、`executeTask` 这类无法表达对象和结果的名字；异步执行器只负责线程池入口，任务的业务逻辑要集中在该执行器的任务函数中。

#### 4.1.2 冗余消除规则

- 同一请求参数只在最外层完成一次 `normalize`、格式校验和枚举转换；内部函数接收已规范化的值，不重复校验。
- 删除只做一层转调的 Helper、Support、Facade、Delegate、Service 接口实现；删除前必须搜索调用方，并将调用方改为真正的业务函数。
- 一个类只保留仍被调用、能表达业务边界或负责基础设施配置的成员；无调用方的函数、接口、配置 Bean、Mapper 方法和事件类应删除。
- 只做同名字段复制时使用 `BeanUtils.copyProperties`；存在字段改名、脱敏、默认值、枚举转换或权限裁剪时才显式赋值。
- 不以“未来可能复用”为理由提前抽象；第一次出现先写清楚，第二次重复时评估，稳定出现两处以上再抽取。
- 重构后必须用 `rg` 检查旧方法名、旧类名和旧配置键，确保没有死代码或残留调用。

#### 4.1.3 过度设计判定

出现下列任一情况，应优先简化：

- 一个函数/类只有一个调用方，却新增接口、抽象类、策略工厂、事件包装或多层代理。
- 为几行同步代码引入线程池、调度器、Future、Outbox、重试框架或状态机，但没有明确的并发、可靠投递或跨事务需求。
- 为一次数据库查询再包一层 Query/Support，调用方仍需继续跳转才能理解查询条件。
- 为一个简单 `switch`、字段赋值或异常转换建立通用模板/基类。
- 同一业务同时存在多套执行入口、重复校验、重复常量和重复状态更新，导致“改一处要找多处”。
- 为解决编译问题保留无调用方的兼容接口；兼容接口必须有明确的外部调用契约和迁移计划。

邮件和消息处理的默认方案：接口只完成必要业务写入并快速返回；Kafka 使用客户端/消费者已有重试配置；超过重试上限只记录失败表并告警。除非有明确的可靠投递、跨事务或业务补偿需求，不新增邮件重试线程池、`sleep`、定时扫描 Outbox 或额外调度线程。

#### 4.1.4 常量、枚举与配置决策

按“含义和作用域”放置，禁止把业务字面量散落在代码中：

| 内容 | 放置位置 |
|------|----------|
| 有限的状态、类型、来源、操作种类 | `model.enums.<domain>` |
| 跨服务共享的错误码、协议字段、Redis 前缀 | `common.constant` 对应领域包 |
| 单个服务内部的固定业务值、默认限制、TTL、锁时长 | `service/<name>/.../common/constant/<domain>/*Constants` |
| 可按环境、部署规模或安全要求调整的值 | `application.yml` + `@ConfigurationProperties` |
| 空字符串等领域统一值 | 对应领域的 `*Strings` 或已有枚举/常量类 |
| 只用于某个类且不会跨类复用的固定值 | 仍放入对应领域的专门常量类，不能散落在业务类顶部 |

判断标准：会改变部署行为的值放配置；需要跨类/跨服务统一的值放常量；表达业务分类的值用枚举；只服务一个类的固定值也放对应领域的专门常量类。业务类中禁止声明新的 `static final` 业务常量。常量类不能变成“杂物箱”，也不能把状态字符串、错误文案和配置值全部堆在一个 `*Constants` 中。

补充约束：

- 跨服务协议字段、Feign 路径、Redis key 前缀、事件类型、限流动作和共享阈值统一放 `common/src/main/java/com/game/community/common/constant/<domain>/`。
- 只属于 Redis/Lua、Kafka、Sentinel、Resilience4j 等中间件实现的脚本或适配对象，留在对应基础设施类中；业务 Controller、DTO 和 Service 接口不得暴露这些类型。
- 环境开关、失败策略、线程池大小和部署相关限额仍放 `application.yml`，由 `@ConfigurationProperties` 或配置注入读取，不硬编码到业务类。

#### 4.1.5 函数注释规范

- **每个函数开头必须有简洁介绍**：说明函数做什么、业务入口/边界是什么；公开函数补充关键参数、返回值和异常语义，私有函数至少说明其业务作用。
- **函数内部只注释关键执行点**：事务/锁/CAS、状态流转、规范化责任、数据库写入、外部服务调用、异步提交、失败回滚、资源清理等必须说明“为什么这样做”；不要逐行翻译代码。
- 注释必须随着逻辑变更同步修改，禁止保留描述旧流程的注释；简单 getter、纯字段映射不写无价值的逐行注释。
- 注释优先使用中文，术语和类名保留英文；关键参数含义直接写在 JavaDoc 的 `@param` 中。

#### 4.1.6 异步、事务与查询边界

- `CompletableFuture.runAsync(...)` 后紧接 `get(...)` 仍然是阻塞调用；需要接口快速返回时，不能在请求线程等待 Future，也不能用线程池任务 `sleep` 实现重试。
- 后台异步不会改变前端调用方式，但会改变返回语义：接口只返回“已提交/已入队”，最终失败通过日志、失败表或通知查询暴露，不能假装已经完成。
- 事务只包住必须原子提交的数据库状态；文件、SMTP、Kafka 等外部 IO 不要伪装成数据库事务的一部分，失败要有明确的清理、失败记录或告警策略。
- Kafka 消息优先使用 Kafka/消费者已有重试和死信能力；仍失败时落失败表并告警。简单业务不要增加定时扫描、Outbox 重试线程、Future 等第二套重试系统。
- 内部主键 `user.id` 只用于本服务和表关联；对外接口、社交接口和跨服务参数使用 `accountId`。涉及多个账号时先批量完成 `accountId -> userId` 映射，再批量查询业务数据，禁止 N+1 查询。
- 用户背包、后台定义和用户搜索等可能增长的数据默认分页；筛选条件由 DTO/Mapper 条件组成，Service 负责归一化和边界限制，Controller 不拼查询逻辑。

#### 4.1.7 主键与中间件隔离

- 数据库内部主键（如 `user.id`、`article.id`、`comment.id`、`report.id`）只允许在 Entity、Mapper、Service 内部和明确的内部 Feign 命令中使用；公开请求、公开响应、公开分页游标和前端路由不得新增内部主键字段。
- 对外使用 `publicId`、`accountId` 或专门的 opaque cursor；不能把数据库主键改名后继续作为“公开 ID”。旧接口若必须兼容数字 ID，应保留边界适配并标注迁移计划。
- 对外 VO 中的内部定位字段必须使用 `ApiJsonViews.Internal` 或独立 Internal VO；公开 Controller 必须显式使用 `ApiJsonViews.Public`，禁止依赖 Jackson 默认视图行为。
- Controller 和领域 Service 不得直接依赖 `StringRedisTemplate`、Sentinel `Entry`/`BlockException`、KafkaTemplate、Resilience4j 注解或内部 token 校验逻辑。限流、熔断、缓存、消息和凭证分别通过 Aspect、Filter、Client、Producer 等适配层提供。
- Sentinel 用于服务容量保护、并发、熔断和接口级流控；跨实例的用户行为配额由独立的业务限额组件实现，不能因为引入 Sentinel 就把业务配额硬塞到 Controller。

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
- 请求 DTO 只包含外部协议字段；Service 解析出的内部主键、缓存 key、Redis 计数、Sentinel 资源名等不得回写到请求 DTO。
- Internal VO 与 Public VO 必须明确区分；需要服务间传递内部定位字段时，优先新增 `*InternalVO`，不要把内部字段默认暴露到公开 VO。

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
- 交付前：**本服务模块测试通过**再提 PR；涉及启动、配置、Bean、线程池、数据库初始化或路由的改动，必须再运行对应启动脚本，看到 `Started *Application` 后才算完成
- user-service 启动检查：`GATEWAY_INTERNAL_SECRET=test SKIP_BUILD=1 ./scripts/services/start-user-service.sh foreground`
- 发现启动失败时，必须继续定位并修复，不能以“编译通过”作为交付结论
- 接口协议或 DTO/VO 发生变化时，至少补一条序列化/绑定测试或服务层边界测试，确认内部主键和中间件字段不会出现在公开响应。

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
- ❌ Controller 直接解析 publicId、拼 SQL 条件、处理分页游标或校验内部 token
- ❌ 公开 DTO/VO 新增数据库内部主键、Redis key、Sentinel 资源名、Kafka topic 或内部凭证字段
- ❌ 把 `StringRedisTemplate`、Sentinel、KafkaTemplate、Feign 失败异常等中间件类型泄漏到 Controller/公开 Service 接口
- ❌ 用多个基础类型参数替代已有复杂业务 DTO

---

## 10. 新增功能检查表

- [ ] 路径符合 §3.2 服务前缀 + 业务子路径
- [ ] Controller 只转发 Service；Service 返回 `Result`
- [ ] DTO/VO/枚举在 `model` 对应包下
- [ ] 写操作有事务；异步有明确线程池或消息
- [ ] 需登录加 `@LoginCheck`；游客可读则登记 gateway 白名单
- [ ] 新 Feign 提供方在 `feign/` 包，路径 `/feign/<domain>/...`
- [ ] 复杂查询/命令已使用语义化 DTO，Controller 没有解析 publicId、游标或内部 token
- [ ] 公开 DTO/VO 没有内部主键和中间件字段；Internal/Public 视图明确
- [ ] 跨服务常量、Redis 前缀、事件类型和限流动作已放入 `common.constant.<domain>`
- [ ] 已用 `rg` 清理旧协议、旧类名、旧配置键和无调用方辅助方法
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
