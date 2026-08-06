# Architecture Flow Docs · Reference

## Manifest Schema

```json
{
  "service": "user-service",
  "base_path": "service/user-service/src/main/java/com/game/community/user",
  "modules": [
    {
      "id": "auth",
      "title": "模块 A · 用户认证",
      "features": [
        {
          "id": "A1",
          "title": "A1 · 发送验证码 POST /user/sendCode",
          "preset": "A1-sendCode",
          "l1": {
            "input": "HTML allowed: <code>String</code>",
            "output": "Result<Void>",
            "purpose": "业务目的一句话"
          },
          "l2": [
            ["1", "步骤动作", "步骤解释"],
            ["2", "...", "..."]
          ],
          "call_tree": {
            "name": "ClassName.methodName",
            "file": "controller/AuthController.java",
            "lines": [33, 37],
            "signature": "public Result<Void> sendCode(...)",
            "role": "HTTP 入口",
            "params": [
              { "name": "dto", "type": "SendCodeDTO", "desc": "..." }
            ],
            "returns": "Result<Void>",
            "children": [ /* 递归同结构 */ ]
          }
        }
      ]
    }
  ]
}
```

### call_tree 规则

1. **根节点** = Controller 或 Scheduler 入口
2. **children** = 被根节点直接调用的下一层（Service / private method）
3. **深度不限**，但 HTML 中统一 `data-depth="4"`（L4）和 `data-depth="5"`（L5），靠 `<ul class="tree">` 缩进表达层级
4. **lines** = `[startLine, endLine]` 1-based， inclusive，用于读源码生成 L5 表
5. **private 方法**同样作为 child 节点

## HTML CSS 类（已在 project-flow.html）

| 类 | 用途 |
|----|------|
| `.io-table` | L4 签名/职责/返回值 |
| `.param-table` | L4 参数表 |
| `.nest-hint` | 调用关系提示 |
| `.chain-summary` | L3 一行调用链 |
| `.l5-grid` | L5 左右两栏 grid |
| `.flow-chart` | 左侧流程图容器 |
| `.flow-node.start/process/decision/redis/call/error/end` | 节点类型 |
| `.flow-title` / `.flow-desc` / `.flow-params` | 节点内容 |
| `.flow-ref` | 源码行号引用 |
| `.source-table` | 行号+代码+注释 |
| `.source-table .tag` | 老师讲读标签 |
| `.eval-box` | 评价与可扩展点 |

## L5 流程节点最小示例

```html
<div class="flow-node call">
  <div class="flow-title">调用业务层 sendCode</div>
  <div class="flow-params">
    <div><strong>phone</strong> · String · dto.getPhone()</div>
    <div><strong>bizType</strong> · String · 决定 Redis Key 与短信模板</div>
  </div>
  <div class="flow-desc">Controller 不写 Redis，委托 Service 完成频控与发码</div>
  <span class="flow-ref">AuthController.java L36</span>
</div>
```

## L5 源码行最小示例

```html
<tr>
  <td class="ln">36</td>
  <td class="code">userAuthService.sendCode(dto.getPhone(), dto.getBizType());</td>
  <td class="cmt"><span class="tag">委托</span>只传两个 String；Service 抛 BusinessException 时全局处理器转 JSON 错误。</td>
</tr>
```

## project-flow.html 标记

```html
<!-- USER-SERVICE-DOCS-START -->
  ... 生成内容 ...
<!-- USER-SERVICE-DOCS-END -->
```

## Markdown 自动块

`level-1-features.md` 中：

```markdown
<!-- USER-SERVICE-AUTO -->
## 模块：user-service（自动生成）
...
<!-- USER-SERVICE-AUTO -->
```

## 生成器启发式（teacher_comment）

脚本 `generate_flow_docs.py` 根据源码关键字推断 tag：
- `BusinessException` → 失败
- `redisUtils` → Redis
- `Mapper` → 持久化
- `sessionHelper` → 会话
- 方法首行 → 入口 + 签名

**复杂链路务必用 preset 或人工改 L5。**

## 编号规范

| 模块 | ID 前缀 | 示例 |
|------|---------|------|
| 认证 | A | A1 sendCode |
| 资料 | B | B1 /user/me |
| 查询 | C | C1 GET /user/{accountId} |
| 账户生命周期 | D | D1 cancel |
| Feign | F | F1 /feign/user/ids |
| 网关 | G | （手工维护） |

## user-service 已覆盖端点（23）

**A1–A5** 认证 · **B1–B4** 资料 · **C1–C5** 查询 · **D1–D6** 账户+定时 · **F1–F3** Feign
