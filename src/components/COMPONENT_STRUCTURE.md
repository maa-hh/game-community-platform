# `components/` 目录结构规范

业务通用组件（`src/components/`）**统一参照 `AppHeader/` 拆分**。`base-ui/` 无业务语义，规则见 `src/base-ui/README.md`。

---

## 一、标准目录（参照 AppHeader）

```
ComponentName/
├── index.tsx          # 编排层：组装 parts + hook，不写复杂业务
├── types.ts           # Props、配置项、类型守卫
├── config.ts          # 静态文案、菜单项、选项（无逻辑）
├── useXxx.ts          # 状态、事件、Redux/路由（组件内复用逻辑）
├── parts/             # 子 UI 块（每个文件一个视觉区域）
│   ├── XxxA.tsx
│   └── XxxB.tsx
└── style.less         # BEM：component-name__element
```

### 各文件职责

| 文件          | 写什么                                     | 不写什么                             |
| ------------- | ------------------------------------------ | ------------------------------------ |
| `index.tsx`   | `memo` 导出、`parts` 拼装、传 props        | 长 `useEffect`、大段 JSX、静态配置表 |
| `types.ts`    | `IProps`、`XxxConfig`、type guard          | 运行时代码                           |
| `config.ts`   | 文案、路由、Segmented options、菜单 schema | `useState`、API                      |
| `useXxx.ts`   | 本组件专用状态与回调；可调 store/service   | JSX                                  |
| `parts/*.tsx` | 单块 UI，接收 props                        | 跨 part 共享状态（放 hook）          |

### 引用约定

- 对外只 `import Xxx from '@/components/Xxx'`，**禁止**深路径 `@/components/Xxx/parts/...`
- 组件内子模块用相对路径：`./parts/HeaderNav`
- 跨目录一律 `@/`

---

## 二、分级要求（避免过度拆分）

| 级别       | 条件                                                  | 最低结构                                                       |
| ---------- | ----------------------------------------------------- | -------------------------------------------------------------- |
| **L 复杂** | `index.tsx` > **80 行**，或多块独立 UI / 多组 handler | `index` + `types` + `useXxx` + `parts/`；有静态表则加 `config` |
| **M 中等** | 40–80 行，或有独立逻辑块                              | `index` + `types`；逻辑抽 `useXxx`                             |
| **S 简单** | < 40 行、单一 DOM 结构                                | `index` + `types`（`IProps` 可单独文件）                       |

**禁止**：为 S 级强行拆 `parts/`；**禁止**：L 级把逻辑全堆在 `index.tsx`。

---

## 三、参考实现：AppHeader

```
AppHeader/
├── index.tsx              # 56 行，只拼装
├── config.ts              # headerNavItems、userMenuSchema
├── types.ts               # HeaderNavItem、UserMenuSchemaItem
├── useHeaderActions.ts    # 搜索、发布、登出、用户菜单
├── parts/
│   ├── HeaderBrand.tsx
│   ├── HeaderNav.tsx
│   ├── HeaderSearch.tsx
│   └── HeaderActions.tsx
└── style.less
```

---

## 四、子目录约定

| 目录       | 说明                                   |
| ---------- | -------------------------------------- |
| `auth/`    | 登录注册表单；共享 `auth/constants.ts` |
| `profile/` | 个人页相关弹窗与 Feed                  |

子目录内**每个组件文件夹**仍遵守本节结构，共享常量放父级 `constants.ts`（等同 `config`）。

---

## 五、与页面（views）的边界

| 场景          | 放哪                                    |
| ------------- | --------------------------------------- |
| ≥2 页复用     | `components/`                           |
| 仅单页使用    | `views/Xxx/components/`（结构同本规范） |
| 无业务原子 UI | `base-ui/`                              |

---

## 六、新增组件检查清单

- [ ] 文件夹名 **PascalCase**，入口 `index.tsx` + `export default memo(...)`
- [ ] `types.ts` 导出 `IProps` 或具名 props 类型
- [ ] 超过 80 行或有多块 UI → `parts/` + `useXxx.ts`
- [ ] 静态选项/文案 → `config.ts`
- [ ] 样式同目录 `style.less`，BEM 与 `DESIGN.md` 一致
- [ ] 不直接 `axios`；异步走 `service/` 或 `dispatch(thunk)`
- [ ] 跑通 `npm run lint` 与 `npx tsc --noEmit`

---

## 七、组件清单与结构状态

| 组件                                  | 级别 | 结构                                                     |
| ------------------------------------- | ---- | -------------------------------------------------------- |
| AppHeader                             | L    | ✅ 标准模板                                              |
| AuthModal                             | L    | index + types + config + useAuthModalContent + parts     |
| CommentSection                        | L    | index + types + useCommentSection + parts                |
| ShareSheet                            | L    | index + types + config + useShareSheet + parts           |
| profile/AccountSecurityModal          | L    | index + types + config + useAccountSecurityModal + parts |
| profile/ProfileFeed                   | L    | index + types + useProfileFeed + parts                   |
| auth/RegisterForm                     | M    | index + types + useRegisterForm + parts                  |
| auth/ResetPasswordForm                | M    | index + types + useResetPasswordForm + parts             |
| ArticleProgressBanner                 | M    | index + config + useArticleProgressPoll                  |
| FeedPanel                             | M    | index + types + useFeedPanel + parts                     |
| PostBottomBar                         | M    | index + types + usePostBottomBar + parts                 |
| CommentItem                           | M    | index + types + config + parts                           |
| ShareCard                             | M    | index + types + useShareCard + parts                     |
| profile/EditUsernameModal 等 4 个弹窗 | M    | index + types + config + useXxx + parts                  |
| auth/LoginForm                        | M    | index + types                                            |
| EmptyState、FollowButton 等 S 级      | S    | index + types                                            |

维护说明：新增或重构组件后更新上表。

---

## 八、相关文档

| 文档                       | 说明         |
| -------------------------- | ------------ |
| `src/components/README.md` | 组件职责索引 |
| `PROJECT_STRUCTURE.md`     | 全局目录规范 |
| `DESIGN.md`                | 视觉与 BEM   |
