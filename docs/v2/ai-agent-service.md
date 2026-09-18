# AI 智能体服务（ai-agent-service）技术文档

## 1. 服务定位

`ai-agent-service` 负责统一内容审核、模型适配和无状态 AI 基础能力。业务服务通过内部 Feign 接口调用，供应商差异由本服务屏蔽；业务索引和检索仍由业务服务自己负责。

当前保留能力：

- 文本、图片、文章三类内容审核。
- 敏感词 AC 自动机快速预审。
- 按 Provider 协议适配器路由模型调用，当前内置 OpenAI Compatible 适配器。
- Embedding 向量生成和搜索关键词扩展。
- 统一结构化审核结果、分数阈值和不可用降级策略。
- 审核提示词集中在 `src/main/resources/prompts` 管理。
- Redis 集群级并发许可、Provider 级限速/并发隔离/熔断和 Prometheus 指标。

明确移除：

- 旧 Agent 编排框架及其依赖。
- RAG 问答、知识文档、知识切片和检索调试接口。
- RAG 知识库、业务 Elasticsearch 索引和业务检索。

## 2. 技术栈和调用边界

- Spring Boot 3.4.2
- Spring AI 1.0.3
- OpenAI Compatible Chat API
- Nacos 注册与配置
- Spring Cloud OpenFeign（由调用方使用）
- Spring Validation、AOP、Springdoc

服务端口：`8093`。

内部接口：

- `POST /feign/ai/moderation`
- `POST /feign/ai/embedding`
- `POST /feign/ai/search-terms`

调用链：

```text
业务服务
  -> Gateway / Feign
  -> ai-agent-service
  -> AgentModelRegistry 选择 provider/model/protocol
  -> ModelProviderAdapter
  -> Provider API
  -> 审核结果 / embedding / 搜索关键词
```

## 3. Provider 配置抽象

配置入口为 `ai-agent.models`，每个 provider 包含 `base-url`、`api-key`、`default-model` 和模型参数；审核请求只传递业务所需的模型标识，不直接依赖供应商 SDK。

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
        base-url: ${AI_DEEPSEEK_BASE_URL:https://api.deepseek.com}
        api-key: ${AI_DEEPSEEK_API_KEY:}
        default-model: ${AI_DEEPSEEK_MODEL:deepseek-chat}
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

## 4. 审核流程

### 4.1 文本审核

1. 校验内容类型和文本长度。
2. 使用 AC 自动机匹配本地敏感词；命中时直接返回拒绝结果，避免消耗模型额度。
3. 未命中时调用文本审核模型。
4. 从模型结果中解析结构化分数，按配置阈值生成 `PASS`、`REJECT` 或 `HUMAN_REVIEW`。
5. 模型调用失败时按 `unavailable-decision` 降级，默认进入人工审核。

### 4.2 图片和文章审核

- 图片通过 Base64 媒体消息发送给视觉模型。
- 文章将正文和图片合并为一次多模态请求，避免正文和图片分别调用造成额外延迟与费用。
- 图片数量、Base64 大小、模型上下文长度需要在网关和服务端同时设置上限。

## 5. 输出和提示词规范

审核结果统一返回：审核类型、决策、总分、风险类别、原因、模型信息和请求耗时。业务服务不解析供应商原始响应。

提示词文件：

- `moderation-system.st`：统一角色、输出格式和安全边界。
- `moderation-text-user.st`：文本审核输入模板。
- `moderation-image-user.st`：图片审核输入模板。
- `moderation-article-user.st`：文章多模态审核输入模板。
- `moderation/sensitive-words.txt`：本地敏感词库。

模型输出必须能够解析为结构化 JSON；解析失败或字段缺失时不得默认放行，应进入配置的不可用降级策略。

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

其中 stage1 验证双实例共享 Redis 并发许可，stage2 验证 Provider Adapter，stage3 验证超时和熔断，stage4 验证图片输入边界，stage5 验证 content-service Feign 熔断配置启动，stage6 验证 Prometheus 指标。
