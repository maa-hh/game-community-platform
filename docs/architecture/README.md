# 项目流程架构文档索引

本目录提供 **5 层分级** 的项目流程说明，供人与 Agent 阅读。

## 层级说明

| 层级 | 文件 | 内容 |
|------|------|------|
| **L1** | [level-1-features.md](./level-1-features.md) | 每个功能的输入、输出、目的 |
| **L2** | [level-2-flows.md](./level-2-flows.md) | 组件间流转过程（含 Mermaid 时序图） |
| **L3** | [level-3-functions.md](./level-3-functions.md) | 函数级调用链 |
| **L4** | [level-4-function-io.md](./level-4-function-io.md) | 每个函数的输入、输出、功能说明 |
| **L5** | [level-5-function-internals.md](./level-5-function-internals.md) | Mermaid 流程图 + 行级注释源码；评价与可扩展点 |

> 注：L4 文件名为 `level-4-function-io.md`

## 交互式 HTML

用浏览器打开 **[project-flow.html](./project-flow.html)**：

- 点击每一层标题逐步展开 L1→L5
- 顶部按钮：**展开到 L1~L4** 或 **一键展开到 L5**
- L5 布局：**L4/L5 按调用链嵌套**（非平铺）；L5 = 流程图（含参数类型/含义）+ 老师讲读级源码表
- **全部折叠** 恢复初始状态

## 覆盖模块

- **user-service（23 个端点）**：见 `<!-- USER-SERVICE-AUTO -->` 自动生成块；manifest：`user-service-manifest.json`
- 网关 JWT 鉴权（gateway）
- 内容发布与任务调度（content-service）
- 社交与 Feed（social-service）
- 通知 SSE（notification-service）

## 生成与维护

```bash
python3 docs/architecture/scripts/generate_flow_docs.py
```

Agent 复刻标准：阅读项目 Skill **architecture-flow-docs**（`.cursor/skills/architecture-flow-docs/SKILL.md`）。

## Agent 使用建议

1. 先读 **L1** 建立功能地图
2. 追踪单条链路：**L2 → L3 → L4 → L5**
3. 排障 Redis/会话/验证码：直接查 **L5 附录 Redis Key 速查**
