# AI Agent 开发指南

本文件供 Cursor / Copilot 等 AI 助手读取。人类开发者请优先阅读 `PROJECT_STRUCTURE.md`。

## 必读

1. **目录规范**：`PROJECT_STRUCTURE.md`（根目录）
2. **设计规范**：`DESIGN.md`（视觉、布局、交互；写前端必遵）
3. **Cursor 规则**：`.cursor/rules/*.mdc`（自动注入会话）
4. **Ant Design Skill**：`.agents/skills/antd/SKILL.md`（写 antd 组件时查 API / demo）
5. **业务组件结构**：`src/components/COMPONENT_STRUCTURE.md`（参照 `AppHeader/` 拆分）

## 项目要点

- React 19 + TS + CRACO + Redux Toolkit + RTK Query + React Router v7 + antd 6.5.1 + axios + LESS
- 路径别名 `@/` → `src/`
- 布局：`Root`（AuthModal）· `Main`（顶栏+内容盒）· `Login`（顶栏+全屏视频，无限宽）
- 请求链：views → dispatch(thunk) → service → HYRequest → mock/API
- 禁止组件直接调用 axios 或散落 Route 配置
- **UI 风格以 `DESIGN.md` 为准**，保障全站统一
- **帖子列表/信息流**：统一 `base-ui/ContentCard`（图文/文章/视频），见 `DESIGN.md` §5.1 与 `src/base-ui/ContentCard/README.md`
- **帖子详情/互动/分享**：先读 `docs/post-detail-design.md`；完整前后端方案见后端仓 `docs/post-detail-social-design.md`（确认后再写代码）
- **中文编码**：全链路 UTF-8；接口乱码先查后端 DB/种子脚本字符集（`game-community-platform/service/CODING_STANDARDS.md` §7.1），见 `PROJECT_STRUCTURE.md` 技术栈表下说明

## 新增页面默认流程

1. 对照 `DESIGN.md` 确认布局壳（Main / Login）与内容宽度
2. `views/<Page>/index.tsx`
3. `router/routes.tsx` 注册到正确 layout 的 children
4. 需要数据时：`service/` + `store/modules/` + thunk
5. **自测通过后再结束**（见下方「交付前自测」）

## 交付前自测（强制）

写完或改完代码后，**在宣告完成前必须自己跑通检查**，不要等用户编译报错再修：

```bash
npm run lint
npx tsc --noEmit
```

- `lint`：ESLint + Prettier，保证风格与常见问题
- `tsc --noEmit`：TypeScript 类型/编译错误（与浏览器里 “Compiled with problems” 同源）
- 若改动涉及启动/路由/登录等行为，再按需 `npm start` 做一次冒烟
- 任一命令失败：先修再回复；不要把红字编译错误留给用户

## 子系统文档

| 主题  | 文件                                    |
| ----- | --------------------------------------- |
| 路由  | `src/router/README.md`                  |
| HTTP  | `src/service/README.md`                 |
| Redux | `src/store/README.md`                   |
| 样式  | `src/assets/css/README.md`              |
| 组件  | `src/components/COMPONENT_STRUCTURE.md` |
| CRACO | `CRACO_GUIDE.md`                        |
