#!/usr/bin/env python3
"""
从 user-service-manifest.json 生成 project-flow.html 的 user-service 区块，
并同步 level-1~5 Markdown 中的 user-service 章节。

用法（仓库根目录）:
  python3 docs/architecture/scripts/generate_flow_docs.py
  python3 docs/architecture/scripts/generate_flow_docs.py --manifest docs/architecture/user-service-manifest.json
"""
from __future__ import annotations

import argparse
import html
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
ARCH = ROOT / "docs" / "architecture"
PRESETS = ARCH / "presets"
HTML_FILE = ARCH / "project-flow.html"
START_MARKER = "<!-- USER-SERVICE-DOCS-START -->"
END_MARKER = "<!-- USER-SERVICE-DOCS-END -->"


def esc(s: str) -> str:
    return html.escape(s, quote=False)


def read_source(base: str, rel_file: str, line_start: int, line_end: int) -> list[tuple[int, str]]:
    path = ROOT / base / rel_file
    if not path.exists():
        return []
    lines = path.read_text(encoding="utf-8").splitlines()
    out = []
    for i in range(line_start, min(line_end, len(lines)) + 1):
        out.append((i, lines[i - 1].rstrip()))
    return out


def infer_tag(line: str) -> str:
    if "throw new BusinessException" in line or "throw new RuntimeException" in line:
        return "失败"
    if "redisUtils" in line or "RedisConstants" in line:
        return "Redis"
    if "Mapper" in line or "mapper." in line:
        return "持久化"
    if "sessionHelper" in line:
        return "会话"
    if "@Transactional" in line or "Transactional" in line:
        return "事务"
    if line.strip().startswith("if ") or line.strip().startswith("} else if"):
        return "分支"
    if line.strip().startswith("return"):
        return "返回"
    if "log." in line or "logOperation" in line:
        return "日志"
    if "EncryptUtils" in line or "JwtUtils" in line:
        return "安全"
    if "auditHelper" in line or "Audit" in line:
        return "审核"
    if "minIO" in line.lower() or "MinIO" in line:
        return "存储"
    return "逻辑"


def teacher_comment(line_no: int, code: str, node: dict) -> str:
    tag = infer_tag(code)
    name = node.get("name", "")
    role = node.get("role", "")
    base = f"<span class=\"tag\">{tag}</span>"
    stripped = code.strip()
    if line_no == node.get("lines", [0, 0])[0] and ("public " in code or "private " in code):
        sig = node.get("signature") or stripped
        return f"{base}方法入口 <code>{esc(name)}</code>。{esc(role)}。签名：<code>{esc(sig)}</code>"
    if "throw new BusinessException" in code:
        return f"{base}业务失败分支：向前端返回可读错误信息，中断后续逻辑。"
    if "redisUtils.get" in code:
        return f"{base}读 Redis Key，判断 Key 是否存在或取值。"
    if "redisUtils.set" in code or "setEx" in code:
        return f"{base}写 Redis，注意 TTL 与 Key 命名规范。"
    if "redisUtils.del" in code:
        return f"{base}删除 Redis Key，通常表示状态重置或一次性消费。"
    if "Mapper" in code and "insert" in code:
        return f"{base}插入数据库记录。"
    if "Mapper" in code and "update" in code:
        return f"{base}更新数据库，常配合乐观锁 version 字段。"
    if "Mapper" in code and "select" in code:
        return f"{base}查询数据库。"
    if stripped.startswith("return"):
        return f"{base}返回给上层调用者，结束本方法或 try 块。"
    return f"{base}{esc(stripped[:120])}{'…' if len(stripped) > 120 else ''}"


def render_source_rows(base: str, node: dict) -> str:
    lines = node.get("lines")
    if not lines:
        return ""
    rel = node.get("file", "")
    src = read_source(base, rel, lines[0], lines[1])
    if not src:
        return f'<tr><td class="ln">—</td><td class="code">（源码未找到 {esc(rel)}）</td><td class="cmt">请检查 manifest 行号</td></tr>'
    rows = []
    fname = Path(rel).name
    method = node.get("name", fname)
    skip_blank_run = 0
    for ln, code in src:
        if not code.strip():
            skip_blank_run += 1
            if skip_blank_run > 2:
                continue
        else:
            skip_blank_run = 0
        display = esc(code).replace("  ", "&nbsp;&nbsp;")
        if "@PostMapping" in code or "@GetMapping" in code or "@PutMapping" in code:
            display = display.replace("@", "<span class=\"ann\">@")
        cmt = teacher_comment(ln, code, node)
        rows.append(f'<tr><td class="ln">{ln}</td><td class="code">{display}</td><td class="cmt">{cmt}</td></tr>')
    return "\n".join(rows)


def render_flow_from_l2(l2: list, node: dict) -> str:
    parts = [
        f'<div class="flow-node start"><div class="flow-title">入口 · {esc(node.get("name",""))}</div>',
    ]
    if node.get("params"):
        parts.append('<div class="flow-params">')
        for p in node["params"]:
            parts.append(f'<div><strong>{esc(p["name"])}</strong> · {esc(p.get("type","?"))} · {esc(p.get("desc",""))}</div>')
        parts.append("</div>")
    parts.append(f'<div class="flow-desc">{esc(node.get("role",""))}</div></div>')
    for i, step in enumerate(l2 or [], 1):
        step_no, action, explain = step[0], step[1], step[2] if len(step) > 2 else ""
        parts.append('<div class="flow-arrow">↓</div>')
        parts.append(
            f'<div class="flow-node process"><div class="flow-title">步骤 {step_no} · {esc(action)}</div>'
            f'<div class="flow-desc">{esc(explain)}</div></div>'
        )
    parts.append('<div class="flow-arrow">↓</div>')
    parts.append(f'<div class="flow-node end"><div class="flow-title">返回 · {esc(node.get("returns","void"))}</div></div>')
    return "\n".join(parts)


def render_flow_simple(node: dict) -> str:
    name = node.get("name", "")
    lines = node.get("lines", [])
    ref = f"L{lines[0]}-{lines[1]}" if lines else ""
    parts = [
        f'<div class="flow-node start"><div class="flow-title">{esc(name)}</div>',
        f'<div class="flow-desc">{esc(node.get("role", node.get("signature", "")))}</div></div>',
        '<div class="flow-arrow">↓</div>',
        f'<div class="flow-node process"><div class="flow-title">执行方法体</div><span class="flow-ref">{ref}</span></div>',
        '<div class="flow-arrow">↓</div>',
        f'<div class="flow-node end"><div class="flow-title">返回/结束</div></div>',
    ]
    return "\n".join(parts)


def render_l5(base: str, node: dict, idx: str, l2: list | None) -> str:
    lines = node.get("lines", [])
    fname = Path(node.get("file", "Unknown.java")).name
    line_ref = f"L{lines[0]}-{lines[1]}" if lines else ""
    flow = render_flow_from_l2(l2, node) if l2 and idx == "1" else render_flow_simple(node)
    source = render_source_rows(base, node)
    header = f"{fname} · {node.get('name','')} · {line_ref}"
    return f"""
                            <li class="node" data-depth="5">
                              <div class="node-head" onclick="toggleNode(this)"><span class="chevron">▶</span><span class="badge b5">L5</span><span class="title">{idx} 行级讲解 · {esc(fname)} {line_ref}</span></div>
                              <div class="node-body">
                                <div class="l5-panel"><div class="l5-grid">
                                  <div><div class="l5-col-title">执行流程</div><div class="flow-chart">
                                    {flow}
                                  </div></div>
                                  <div><div class="l5-col-title">源码 · 老师讲读</div><div class="source-panel">
                                    <div class="source-header">{esc(header)}</div>
                                    <table class="source-table">
                                      {source}
                                    </table></div></div>
                                </div></div>
                              </div>
                            </li>"""


def render_param_table(params: list) -> str:
    if not params:
        return ""
    rows = ['<table class="param-table"><tr><th>参数名</th><th>类型</th><th>含义</th></tr>']
    for p in params:
        rows.append(
            f'<tr><td><code>{esc(p["name"])}</code></td><td><code>{esc(p.get("type",""))}</code></td><td>{esc(p.get("desc",""))}</td></tr>'
        )
    rows.append("</table>")
    return "\n".join(rows)


def render_l4_node(base: str, node: dict, idx: int, parent_ref: str, l2: list | None, counter: list) -> str:
    counter[0] += 1
    num = counter[0]
    roman = ["①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧", "⑨", "⑩"]
    label = roman[num - 1] if num <= len(roman) else f"{num}"
    name = node.get("name", "unknown")
    nest = f'<span class="nest-hint">↳ 被 {parent_ref} 调用</span>' if parent_ref else '<span class="nest-hint">· HTTP/入口</span>'
    if not parent_ref and node.get("role"):
        nest = f'<span class="nest-hint">· {esc(node["role"])}</span>'

    io_rows = []
    if node.get("signature"):
        io_rows.append(f'<tr><th>完整签名</th><td><code>{esc(node["signature"])}</code></td></tr>')
    elif name:
        io_rows.append(f'<tr><th>方法</th><td><code>{esc(name)}</code></td></tr>')
    if node.get("role"):
        io_rows.append(f'<tr><th>职责</th><td>{esc(node["role"])}</td></tr>')
    if node.get("returns"):
        io_rows.append(f'<tr><th>返回值</th><td><code>{esc(node["returns"])}</code></td></tr>')
    lines = node.get("lines")
    if lines:
        io_rows.append(f'<tr><th>源码</th><td><code>{esc(Path(node.get("file","")).name)}</code> L{lines[0]}-{lines[1]}</td></tr>')

    io_table = f'<table class="io-table">{"".join(io_rows)}</table>' if io_rows else ""
    param_table = render_param_table(node.get("params") or [])

    child_html = ""
    children = node.get("children") or []
    child_refs = []
    if children:
        child_parts = []
        for child in children:
            child_ref = f"{label}-L{node.get('lines', ['?', '?'])[0]}"
            child_parts.append(render_l4_node(base, child, num, child_ref, None, counter))
        child_html = f'<ul class="tree">{"".join(child_parts)}</ul>'

    l5 = render_l5(base, node, label, l2 if num == 1 else None)
    inner_tree = f"<ul class=\"tree\">{l5}{child_html}</ul>"

    return f"""
                      <li class="node" data-depth="4">
                        <div class="node-head" onclick="toggleNode(this)"><span class="chevron">▶</span><span class="badge b4">L4</span><span class="title">{label} {esc(name.split("(")[0])} {nest}</span></div>
                        <div class="node-body">
                          {io_table}
                          {param_table}
                          {inner_tree}
                        </div>
                      </li>"""


def chain_summary(tree: dict) -> str:
    names = []

    def walk(n):
        names.append(n.get("name", "").split("(")[0])
        for c in n.get("children") or []:
            walk(c)

    walk(tree)
    return " → ".join(f"<code>{esc(n)}</code>" for n in names)


def render_l2_table(l2: list) -> str:
    if not l2:
        return "<p class=\"chain-summary\">（见 L3 调用链）</p>"
    rows = ['<table><tr><th>步骤</th><th>动作</th><th>解释</th></tr>']
    for step in l2:
        rows.append(f'<tr><td>{esc(step[0])}</td><td>{esc(step[1])}</td><td>{esc(step[2] if len(step)>2 else "")}</td></tr>')
    rows.append("</table>")
    return "\n".join(rows)


def render_l3_l5(base: str, feature: dict) -> str:
    preset = feature.get("preset")
    if preset:
        preset_path = PRESETS / f"{preset}.l2-l5.html"
        if preset_path.exists():
            return preset_path.read_text(encoding="utf-8")
    tree = feature.get("call_tree")
    if not tree:
        return "<p>（manifest 缺少 call_tree）</p>"
    counter = [0]
    l4_html = render_l4_node(base, tree, 0, "", feature.get("l2"), counter)
    return f"""
        <ul class="tree">
          <li class="node" data-depth="2">
            <div class="node-head" onclick="toggleNode(this)"><span class="chevron">▶</span><span class="badge b2">L2</span><span class="title">流转过程</span></div>
            <div class="node-body">{render_l2_table(feature.get("l2") or [])}</div>
          </li>
          <li class="node" data-depth="3">
            <div class="node-head" onclick="toggleNode(this)"><span class="chevron">▶</span><span class="badge b3">L3</span><span class="title">函数调用链（L4→L5 按调用关系嵌套）</span></div>
            <div class="node-body">
              <p class="chain-summary">{chain_summary(tree)}</p>
              <ul class="tree">{l4_html}</ul>
            </div>
          </li>
        </ul>"""


def render_feature_l1(manifest: dict, feature: dict) -> str:
    base = manifest["base_path"]
    l1 = feature["l1"]
    fid = feature["id"]
    title = feature["title"]
    body = render_l3_l5(base, feature)
    return f"""
    <li class="node" data-depth="1">
      <div class="node-head" onclick="toggleNode(this)">
        <span class="chevron">▶</span><span class="badge b1">L1</span>
        <span class="title">{esc(title)}</span>
      </div>
      <div class="node-body">
        <table>
          <tr><th>输入</th><td>{l1["input"]}</td></tr>
          <tr><th>输出</th><td>{l1["output"]}</td></tr>
          <tr><th>目的</th><td>{l1["purpose"]}</td></tr>
        </table>
        {body}
      </div>
    </li>"""


def render_html(manifest: dict) -> str:
    parts = [START_MARKER, f'    <!-- user-service · generated by generate_flow_docs.py -->']
    for mod in manifest["modules"]:
        parts.append(f'    <!-- {mod["title"]} -->')
        for feat in mod["features"]:
            parts.append(render_feature_l1(manifest, feat))
    parts.append(f"    {END_MARKER}")
    return "\n".join(parts)


def patch_html(manifest: dict) -> None:
    html_path = HTML_FILE
    content = html_path.read_text(encoding="utf-8")
    generated = render_html(manifest)

    if START_MARKER in content and END_MARKER in content:
        pattern = re.compile(re.escape(START_MARKER) + r".*?" + re.escape(END_MARKER), re.DOTALL)
        new_content = pattern.sub(generated.strip(), content)
    else:
        legacy = re.compile(
            r"    <!-- ========== 发送验证码.*?\n    <!-- ========== 网关 ========== -->",
            re.DOTALL,
        )
        if legacy.search(content):
            new_content = legacy.sub(generated.strip() + "\n\n    <!-- ========== 网关 ========== -->", content)
        else:
            raise SystemExit("cannot find user-service section to replace; add markers or legacy comment")

    html_path.write_text(new_content, encoding="utf-8")
    print(f"patched {html_path}")


def render_md_l1(manifest: dict) -> str:
    lines = ["## 模块：user-service（自动生成）", ""]
    for mod in manifest["modules"]:
        lines.append(f"### {mod['title']}")
        lines.append("")
        for feat in mod["features"]:
            l1 = feat["l1"]
            lines.append(f"#### {feat['title']}")
            lines.append("| 项 | 说明 |")
            lines.append("|----|------|")
            lines.append(f"| **输入** | {l1['input']} |")
            lines.append(f"| **输出** | {l1['output']} |")
            lines.append(f"| **目的** | {l1['purpose']} |")
            lines.append("")
    return "\n".join(lines)


def patch_markdown(manifest: dict) -> None:
    marker = "<!-- USER-SERVICE-AUTO -->"
    block = marker + "\n" + render_md_l1(manifest) + "\n" + marker
    for name in ["level-1-features.md"]:
        path = ARCH / name
        text = path.read_text(encoding="utf-8")
        if marker in text:
            text = re.sub(re.escape(marker) + r".*?" + re.escape(marker), block, text, flags=re.DOTALL)
        else:
            text = text.rstrip() + "\n\n" + block + "\n"
        path.write_text(text, encoding="utf-8")
        print(f"patched {path}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", default=str(ARCH / "user-service-manifest.json"))
    parser.add_argument("--html-only", action="store_true")
    args = parser.parse_args()
    manifest = json.loads(Path(args.manifest).read_text(encoding="utf-8"))
    patch_html(manifest)
    if not args.html_only:
        patch_markdown(manifest)


if __name__ == "__main__":
    main()
