# AI 智能体服务（ai-agent-service）技术文档

## 1. 服务定位

`ai-agent-service` 负责统一内容审核、模型适配和无状态 AI 基础能力。内容、用户和搜索服务通过 Kafka 投递 AI 任务，由本服务异步执行并回写结果；供应商差异由本服务屏蔽，业务索引和检索仍由业务服务自己负责。

当前保留能力：

- 文本、图片、文章三类内容审核。
- 敏感词 AC 自动机快速预审。
- 按 Provider 协议适配器路由模型调用，当前内置 OpenAI Compatible 适配器。
- Embedding 向量生成和搜索关键词扩展。
- 统一结构化审核结果、分数阈值和不可用降级策略。
- 审核提示词集中在 `src/main/resources/prompts` 管理。
- 搜索扩词使用 `search-terms-system.st` 模板，最多返回 5 个去重关键词。
- Redis 集群级并发许可、Provider 级限速/并发隔离/熔断和 Prometheus 指标。

明确移除：

- 旧 Agent 编排框架及其依赖。
- RAG 问答、知识文档、知识切片和检索调试接口。
- RAG 知识库、业务 Elasticsearch 索引和业务检索。

## 2. 技术栈和调用边界

- Spring Boot 3.4.2
- Spring AI 1.0.3
- OpenAI Compatible Chat API
- Spring Kafka（AI 任务削峰与结果回写）
- Nacos 注册与配置
- Spring Validation、AOP、Springdoc

服务端口：`8093`。

兼容/诊断接口（业务链路不再依赖这些 HTTP 调用）：

- `POST /feign/ai/moderation`
- `POST /feign/ai/embedding`
- `POST /feign/ai/search-terms`

正式业务调用链：

```text
content-service / user-service / search-service
  -> AiTaskProducer
  -> Kafka: ai-task-request-events
  -> AiTaskListener
  -> AgentModelRegistry 选择 provider/model/protocol
  -> ModelProviderAdapter
  -> Provider API
  -> Kafka: ai-task-result-events
  -> 业务服务结果监听器
```

任务类型为 `MODERATION`、`EMBEDDING`、`SEARCH_TERMS`。请求消息携带业务快照和 `jobId`，结果消息携带成功状态、结果载荷或失败原因；业务侧按任务状态/CAS 处理重复结果。

## 3. Provider 配置抽象

配置入口为 `ai-agent.models`，每个 provider 包含 `base-url`、`api-key`、能力模型和模型参数；审核请求只传递业务所需的模型标识，不直接依赖供应商 SDK。

`AgentModelRegistry` 负责：

1. 根据 provider 和能力解析最终模型配置。
2. 按 `provider + model` 缓存 ChatClient 和 EmbeddingClient，避免每次请求重复创建客户端。
3. 按 `protocol` 选择 `ModelProviderAdapter`；OpenAI Compatible 由独立适配器负责构造客户端和请求工厂。
4. 对缺少 provider、模型不存在、配置不完整或协议不支持等情况统一抛出配置异常。

新增非兼容供应商时，实现 `ModelProviderAdapter` 并注册对应 `protocol`，不在审核业务中增加供应商分支。Provider 配置同时包含连接超时、读取超时、每秒请求数、最大并发数和熔断窗口参数。

配置示例：

```yaml
ai-agent:
  models:
    default-provider: deepseek
    default-embedding-provider: dashscope
    providers:
      deepseek:
        base-url: ${DEEPSEEK_BASE_URL:https://api.deepseek.com}
        api-key: ${DEEPSEEK_API_KEY:}
        chat-model: ${DEEPSEEK_CHAT_MODEL:deepseek-flash}
        text-moderation-model: ${DEEPSEEK_TEXT_MODEL:deepseek-flash}
        image-moderation-model: ${DEEPSEEK_IMAGE_MODEL:deepseek-flash}
        protocol: openai-compatible
        completions-path: ${DEEPSEEK_COMPLETIONS_PATH:/chat/completions}
        temperature: 0.0
      dashscope:
        protocol: openai-compatible
        base-url: https://dashscope.aliyuncs.com/compatible-mode
        completions-path: /v1/chat/completions
        embeddings-path: /v1/embeddings
        api-key: ${DASHSCOPE_API_KEY:}
        chat-model: qwen-plus
        embedding-model: text-embedding-v4
```

当前默认审核和搜索扩词 Provider 为 DeepSeek；向量生成默认保留 DashScope，因为 DeepSeek 当前配置未提供 embedding 接口。若切换到提供向量能力的其他 Provider，只需替换 `default-embedding-provider` 和对应配置，不修改业务服务。

## 4. 审核流程

### 4.1 文本审核

1. 校验内容类型和文本长度。
2. 使用 AC 自动机匹配本地敏感词；命中时直接返回拒绝结果，避免消耗模型额度。
3. 未命中时调用文本审核模型。
4. 从模型结果中解析结构化分数，按配置阈值生成 `PASS`、`REJECT` 或 `HUMAN_REVIEW`。
5. 模型调用失败时按 `unavailable-decision` 降级，默认进入人工审核。

### 4.2 图片和文章审核

- 图片优先通过 MinIO 短时签名 URL 发送；只有 `data:` 图片才保留 Base64 兼容路径。
- 文章将正文和图片合并为一次多模态请求，避免正文和图片分别调用造成额外延迟与费用。
- 图片数量、Base64 大小、模型上下文长度需要在网关和服务端同时设置上限。

## 5. 输出和提示词规范

审核结果统一返回：审核类型、决策、总分、风险类别、原因、模型信息和请求耗时。业务服务不解析供应商原始响应。

提示词文件：

- `moderation-system.st`：统一角色、输出格式和安全边界。
- `moderation-text-user.st`：文本审核输入模板。
- `moderation-image-user.st`：图片审核输入模板。
- `moderation-article-user.st`：文章多模态审核输入模板。
- `search-terms-system.st`：搜索扩词角色、JSON 输出约束和关键词数量上限。
- `moderation/sensitive-words.txt`：本地敏感词库。

模型输出必须能够解析为结构化 JSON；解析失败或字段缺失时不得默认放行，应进入配置的不可用降级策略。搜索扩词在提示词和服务端均硬限制为最多 5 个去重关键词。

## 6. 并发和多实例说明

当前实现包含三层保护：

- 服务实例内使用 `Semaphore` 限制同时进入模型调用的请求数。
- Redis Lua 令牌/许可计数按 Provider 维护集群级并发上限，多实例共享同一配额；Redis 不可用时拒绝进入模型调用并转人工审核。
- Provider 级 Resilience4j 限速、并发隔离和熔断；JDK HTTP 客户端按 Provider 使用独立连接超时和读取超时。

调用方仍应使用有界线程池，避免将模型慢调用无限制地堆积在业务服务内存中。集群实际容量还受供应商 QPS、Token 速率、连接池和模型延迟限制。

上线高并发时需要配置和观测：

1. 为 Provider 配置 Redis 全局并发上限、每实例并发上限、每秒请求数和熔断窗口。
2. 为入口和业务调用方配置有界线程池，并通过压测校准实例数、连接池和供应商配额。
3. 采集请求量、审核耗时、Provider 调用成功/失败、降级次数、超时、限流和熔断状态。
4. 图片 Base64 大小、文章图片数量和远程图片 Host 白名单均在模型调用前校验。

## 7. 配置安全

- API Key 只能通过环境变量或 Nacos 加密配置注入，禁止提交明文密钥。
- 生产环境不应暴露完整 Actuator 端点和详细健康信息。
- 生产环境应关闭 DEBUG 日志和不必要的 Swagger 暴露。
- 日志不得输出原始文本、Base64 图片、API Key 或完整模型响应。
- 远程图片 URL 默认禁止；必须同时显式开启开关并配置精确 Host 白名单。

## 8. 验证方式

```bash
mvn -pl service/ai-agent-service -am clean test
```

同时应确认：

- 仓库中不存在旧 Agent 编排依赖或 AI 服务旧 RAG 类名。
- Redis 仅用于集群级审核并发许可，不再用于 RAG、聊天记忆或知识库。
- 文本、图片、文章三类审核均覆盖成功、敏感词命中、模型失败和结构化解析失败场景。

仓库提供真实启动验证脚本：

```bash
scripts/verify/ai-hardening-stage.sh stage1
scripts/verify/ai-hardening-stage.sh stage2
scripts/verify/ai-hardening-stage.sh stage3
scripts/verify/ai-hardening-stage.sh stage4
scripts/verify/ai-hardening-stage.sh stage5
scripts/verify/ai-hardening-stage.sh stage6
```

其中 stage1 验证双实例共享 Redis 并发许可，stage2 验证 Provider Adapter，stage3 验证超时和熔断，stage4 验证图片输入边界，stage5 验证 content-service 的 Kafka AI 审核投递/结果监听配置，stage6 验证 Prometheus 指标。
