---
name: architecture-flow-docs
description: >-
  Generates 5-layer architecture flow docs (L1-L5) for microservice endpoints in
  project-flow.html and level-1~5.md. Produces nested L4/L5 call-chain HTML with
  flowcharts, param tables, and teacher-style source annotations. Use when adding
  or updating architecture docs, project-flow.html, level-*-*.md, user-service
  manifest, or when the user asks to document API flows to the current standard.
---

# Architecture Flow Docs（5 层流程文档）

## 目标输出

| 产物 | 路径 |
|------|------|
| 交互 HTML | `docs/architecture/project-flow.html` |
| L1 功能 | `docs/architecture/level-1-features.md` |
| Manifest | `docs/architecture/{service}-manifest.json` |
| 生成脚本 | `docs/architecture/scripts/generate_flow_docs.py` |
| 精细预设 | `docs/architecture/presets/{id}-{slug}.l2-l5.html` |

## 快速开始（user-service 已配置）

```bash
# 编辑 manifest 后重新生成
python3 docs/architecture/scripts/generate_flow_docs.py

# 浏览器查看
open docs/architecture/project-flow.html
```

## 5 层标准（必须全部满足）

### L1 · 功能
- 表格三行：**输入**（字段 + 类型 + 约束）、**输出**（Result 泛型）、**目的**（业务目标句式，非实现总结）

### L2 · 流转
- 步骤表：步骤号 | 动作 | 解释（每步说明「为什么」）

### L3 · 调用链
- `chain-summary` 一行：`A → B → C`
- **L4/L5 必须按调用关系嵌套**，禁止平铺同级

### L4 · 函数 IO（每个 call_tree 节点）
- `io-table`：完整签名、职责、返回值、源码文件行号
- `param-table`：参数名 | 类型 | 含义
- 标题带 `nest-hint`：`↳ 被 ①-L36 调用`
- 子调用放在 `<ul class="tree">` 内，作为父 L4 的 **嵌套 li**，不是兄弟

### L5 · 行级（每个 L4 下）
- 左：`flow-chart` — 每节点含 `flow-title` + `flow-params`（名·类型·含义）+ `flow-desc`
- 右：`source-table` — 行号 | 源码 | 老师讲读（`<span class="tag">映射/Redis/…</span>` + 逐行解释）
- 节点类型：`start` / `process` / `decision` / `redis` / `call` / `error` / `end`
- 可选 `eval-box`：评价 + 可扩展点

## 调用链嵌套结构（HTML 模板）

```html
<!-- L3 -->
<ul class="tree">
  <li class="node" data-depth="4">          <!-- ① Controller -->
    <div class="node-body">
      <table class="io-table">...</table>
      <table class="param-table">...</table>
      <ul class="tree">
        <li class="node" data-depth="5">...</li>   <!-- L5 ① -->
        <li class="node" data-depth="4">            <!-- ② Service，嵌套在 ① 内 -->
          <ul class="tree">
            <li class="node" data-depth="5">...</li>
            <li class="node" data-depth="4">...</li>  <!-- ③ 更深 -->
          </ul>
        </li>
      </ul>
    </div>
  </li>
</ul>
```

**禁止**：6 个 `data-depth="4"` 平铺在 L3 下。

## 新增/更新一个端点的流程

### 1. 读源码，填 manifest

编辑 `docs/architecture/user-service-manifest.json`（或其他 `{service}-manifest.json`）：

```json
{
  "id": "A6",
  "title": "A6 · 新接口 POST /user/foo",
  "l1": { "input": "...", "output": "...", "purpose": "..." },
  "l2": [["1", "动作", "解释"], ["2", "...", "..."]],
  "call_tree": {
    "name": "FooController.bar",
    "file": "controller/FooController.java",
    "lines": [10, 15],
    "signature": "public Result<Void> bar(@RequestBody FooDTO dto)",
    "role": "HTTP 入口",
    "params": [{ "name": "dto", "type": "FooDTO", "desc": "..." }],
    "returns": "Result<Void>",
    "children": [
      {
        "name": "FooServiceImpl.bar",
        "file": "service/impl/FooServiceImpl.java",
        "lines": [20, 45],
        "role": "业务逻辑",
        "children": []
      }
    ]
  }
}
```

字段说明见 [reference.md](reference.md)。

### 2. 精细文档用 preset（可选）

复杂链路（如发验证码 6 层调用）手写 L2-L5，保存为：

`docs/architecture/presets/A1-sendCode.l2-l5.html`

manifest 中加 `"preset": "A1-sendCode"`，生成器会嵌入 preset 而非自动生成。

### 3. 运行生成器

```bash
python3 docs/architecture/scripts/generate_flow_docs.py
```

HTML 替换 `<!-- USER-SERVICE-DOCS-START -->` … `END -->` 区块。

### 4. 人工补强（生成器产出后必做）

生成器会读源码行 + 启发式注释，**复杂方法仍需人工**：
- 补全 L5 流程图分支（if/Redis/异常）
- 把 `tag` 注释写到「老师讲读」深度（参考 A1 preset）
- 核对 manifest `lines` 与 IDE 行号一致

### 5. 验证

- [ ] L4 调用链是否嵌套（展开 L3 应看到树形缩进）
- [ ] L5 流程节点是否含参数类型/含义（非仅函数名）
- [ ] 源码表是否覆盖方法关键行
- [ ] `project-flow.html` 一键展开到 L5 无 HTML 断行
- [ ] 网关/其他模块区块未被误删

## 源码注释 tag 词汇表

| tag | 用于 |
|-----|------|
| 映射 | @PostMapping / @GetMapping |
| 签名 | 方法声明行 |
| 委托 | Controller 调 Service |
| 响应 | return Result |
| 入口 | Service 方法第一行 |
| Redis | get/setEx/del |
| 持久化 | Mapper CRUD |
| 会话 | sessionHelper |
| 失败 | throw BusinessException |
| 安全 | JWT / BCrypt |
| 审核 | auditHelper |
| 存储 | MinIO |

## 扩展其他微服务

1. 复制 `user-service-manifest.json` → `content-service-manifest.json`
2. 修改 `base_path` 与 `modules`
3. 扩展 `generate_flow_docs.py` 支持 `--service content` 与独立 HTML 标记（或合并 manifest）
4. 复杂 preset 放 `docs/architecture/presets/`

## 参考

- 完整 CSS 类与示例：[reference.md](reference.md)
- 黄金标准 preset：`docs/architecture/presets/A1-sendCode.l2-l5.html`
- 现有 user-service manifest：`docs/architecture/user-service-manifest.json`
