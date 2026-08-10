# 项目目录结构与代码组织规范

本项目 `src/` 采用按职责分层的方式组织代码。每个文件夹有明确的单一职责，所有新增代码必须按本规范归位，禁止随意堆放。

> **AI 开发约定：** Cursor 会通过 `.cursor/rules/` 自动读取本规范，开发前请先确认代码归属。

---

## 一、技术栈与全局约定

| 类别  | 技术                     | 说明                                                        |
| ----- | ------------------------ | ----------------------------------------------------------- |
| 框架  | React 19 + TypeScript    | 函数组件 + Hooks，不用 class                                |
| 构建  | CRA + CRACO              | `@/` 别名、LESS、不 eject                                   |
| 路由  | React Router v7          | 数据路由 `createBrowserRouter` + History 模式               |
| 状态  | Redux Toolkit            | `createAsyncThunk` 发请求，`useAppSelector` 读数据          |
| 请求  | axios（HYRequest 封装）  | 组件不直接调 axios，走 `service/`                           |
| UI 库 | Ant Design 5             | 主题在 `App.tsx` 的 `ConfigProvider`；`index.less` 引 reset |
| 样式  | LESS + styled-components | 全局用 LESS；`base-ui`/布局可用 styled-components           |
| Mock  | mockjs                   | 开发环境拦截 API，见 `src/mock/`                            |

**字符集（中文不乱码）：**

- 源码与 JSON/LESS：**UTF-8**；`public/index.html` 已设 `<meta charset="utf-8" />`
- API 响应为 UTF-8 JSON，axios 默认按 UTF-8 解析；**前端一般无需额外转码**
- 若接口中文显示乱码，优先排查后端 DB 写入（见后端 `service/CODING_STANDARDS.md` §7.1），不要在前端做 `decodeURIComponent` 等补丁
- Mock 数据、`.env`、含中文的注释/文案文件同样保存为 UTF-8

**详细子文档：**

| 文档     | 路径                       |
| -------- | -------------------------- |
| 路由     | `src/router/README.md`     |
| 样式     | `src/assets/css/README.md` |
| 请求层   | `src/service/README.md`    |
| 状态管理 | `src/store/README.md`      |
| 基础组件 | `src/base-ui/README.md`    |
| CRACO    | `CRACO_GUIDE.md`           |

---

## 二、目录总览

```
src/
├── assets/        # 静态资源（图片、全局 LESS）
├── base-ui/       # 基础 UI（无业务语义，可封装 antd）
├── components/    # 业务通用组件（如 AppHeader、UserCard）
├── hooks/         # 全局自定义 Hook
├── layouts/       # 页面布局壳子（Main、Auth 等）
├── mock/          # 开发环境 mock 数据
├── router/        # 路由表 + RouterProvider
├── service/       # axios 封装 + API
├── store/         # Redux 模块 + 类型化 hooks
├── utils/         # 纯工具函数
├── views/         # 页面级组件（不管布局壳子）
├── App.tsx        # Provider 装配（Redux、antd、Router）
└── index.tsx      # 入口（全局样式、mock）
```

---

## 三、布局规范（layouts/）

**原则：不同页面类型用不同布局壳子，不要全部塞进一个 Main。**

| 布局       | 路径            | 用于       | 包含                                |
| ---------- | --------------- | ---------- | ----------------------------------- |
| MainLayout | `layouts/Main/` | 站内浏览页 | Header + `<Outlet />` + 可选 Footer |
| AuthLayout | `layouts/Auth/` | 登录、注册 | 全屏居中卡片，**无 Header**         |
| 无布局     | —               | 404 等     | 直接渲染页面                        |

### 路由嵌套示例

```tsx
// router/routes.tsx
{
  path: '/',
  element: <MainLayout />,
  children: [
    { path: '/', element: <Home /> },
    { path: 'recommend', element: <Recommend /> },
  ],
},
{
  path: '/',
  element: <AuthLayout />,
  children: [
    { path: 'login', element: <Login /> },
    { path: 'register', element: <Register /> },
  ],
},
{ path: '*', element: <NotFound /> },
```

### 职责划分

| 内容                        | 放哪                    |
| --------------------------- | ----------------------- |
| 布局壳子（Header + 内容区） | `layouts/Main/`         |
| 顶栏导航、Logo、用户区      | `components/AppHeader/` |
| 登录表单 UI                 | `views/Login/`          |
| 全屏居中背景                | `layouts/Auth/`         |

**页面（views）不写 Header，布局由 layouts 负责。**

---

## 四、各文件夹作用与使用规范

### 1. `assets/` — 静态资源

```
assets/
├── css/           # 全局 LESS（index / reset / common）
├── images/        # 图片
└── icons/         # 图标
```

- 全局样式入口：`src/index.tsx` 引入 `@/assets/css/index.less`。
- 引入顺序：`normalize` → `reset.less` → `antd reset` → `common.less`。
- 业务组件样式跟组件走，不堆在 `index.less`。

---

### 2. `base-ui/` — 基础 UI 组件

- **无业务语义**，可封装 antd（如 `Button`、`Input`、`Avatar`）。
- 不含业务请求、不读 store。
- 写法：`interface IProps` + `FC<IProps>` + `memo` 导出。
- 样式：styled-components 或组件目录内 less。

```
base-ui/
├── Button/
│   └── index.tsx
└── InfoCard/      # 示例
    └── index.tsx
```

**判断：** 去掉业务名还能用 → `base-ui`；否则 → `components`。

---

### 3. `components/` — 业务通用组件

- ≥2 个页面复用才放这里。
- 可读 `store`、调 `service`，路由跳转放页面或 router。
- **目录结构**统一参照 `AppHeader/`，规范见 `src/components/COMPONENT_STRUCTURE.md`（`index` + `types` + `useXxx` + `config` + `parts/`）。
- 示例：`AppHeader/`、`AuthModal/`、`CommentSection/`。

---

### 4. `hooks/` — 自定义 Hook

- 命名 `useXxx.ts`，跨页面复用。
- 页面独有 Hook 放 `views/X/hooks/`。
- 示例：`useAuth.ts`（登录态、登出）。

---

### 5. `layouts/` — 布局壳子

- 只负责拼装结构（Header、Outlet、Footer），不写具体业务页面逻辑。
- 一个布局一个目录：`layouts/Main/`、`layouts/Auth/`。

---

### 6. `router/` — 路由

- 路由表 + `createBrowserRouter`，不散落 `<Route>`。
- 数据路由 API，History 模式（URL 无 `#`）。
- 首屏同步 import，次要页 `lazy()` + `Suspense`。
- 守卫放 `router/guards.ts`。

---

### 7. `service/` — 网络请求

- 组件**不直接调 axios**，走 `hyRequest.get/post/...`。
- 按模块拆分：`home.ts`、`recommend.ts`、`auth.ts`。
- 配置：`service/config.ts`；封装：`service/request.ts`。
- 环境变量：`REACT_APP_BASE_URL`、`REACT_APP_ENABLE_MOCK`。

**数据流：**

```
views dispatch(thunk) → store createAsyncThunk → service API → HYRequest → mock/后端
```

---

### 8. `store/` — 全局状态

- 网络请求用 `createAsyncThunk`，不在组件里直接 `getBanners()`。
- 异步状态用 `extraReducers` 监听 `pending/fulfilled/rejected`。
- 使用 `useAppSelector`、`useAppDispatch`，多字段选取加 `appShallowEqual`。
- 按模块：`store/modules/home.ts`、`counter.ts` 等。

---

### 9. `utils/` — 工具函数

- 纯函数，无副作用。
- 示例：`storage.ts`（token）、`format.ts`、`validate.ts`。

---

### 10. `views/` — 页面

- 每个路由一个目录，入口 `index.tsx`。
- **不管布局**（布局在 layouts）。
- 页面独有子组件放 `views/X/components/`。
- 通过 `dispatch(thunk)` + `useAppSelector` 拿数据。

---

### 11. `mock/` — 开发 Mock

- 仅开发环境在 `index.tsx` 引入。
- 用 `Mock.mock(url, method, data)` 拦截 API。

---

## 五、样式规范

| 层级            | 用什么                    | 放哪                                  |
| --------------- | ------------------------- | ------------------------------------- |
| 全局 reset/变量 | LESS                      | `assets/css/`                         |
| antd 基础 reset | CSS                       | `index.less` 引 `antd/dist/reset.css` |
| antd 主题色     | ConfigProvider            | `App.tsx`（与 `@primary-color` 一致） |
| 布局 / Header   | styled-components 或 less | `layouts/`、`components/`             |
| base-ui 组件    | styled-components         | `base-ui/X/`                          |
| 页面独有        | less 或 styled            | `views/X/`                            |

**一个组件只选一种样式方案，不要混用三种。**

---

## 六、组件写法规范

```tsx
// 标准函数组件模板（base-ui / components）
import React, { memo } from 'react';
import type { FC } from 'react';

interface IProps {
  title: string;
  size?: 'small' | 'large';
}

const MyComponent: FC<IProps> = (props) => {
  return <div>{props.title}</div>;
};

export default memo(MyComponent);
```

- 使用函数组件，不用 class。
- 路由页面用 `element={<Home />}`（v6+），不用 `component={Home}`。

---

## 七、根文件职责

| 文件                 | 职责                                             |
| -------------------- | ------------------------------------------------ |
| `App.tsx`            | Redux Provider + antd ConfigProvider + AppRouter |
| `index.tsx`          | 全局 LESS、开发 mock、演示 token                 |
| `react-app-env.d.ts` | 环境变量类型（`REACT_APP_*`）                    |

---

## 八、模块依赖方向

```
views/ ──► layouts/ ──► components/ ──► base-ui/
   │           │
   ├──► hooks/
   ├──► service/
   ├──► store/
   ├──► utils/
   └──► router/
```

**禁止：**

- `base-ui` 引用 `components` / `views` / `store` / `service`
- `utils` 引用任何业务目录
- `service` / `store` 引用 `views` / `components`
- 组件里散落 `<Route>`

---

## 九、导入路径规范

- 一律 `@/` 别名，禁止 `../../../`。
- 第三方 import 在前，项目内 import 在后，中间空一行。

```ts
import { Button } from 'antd';
import { useEffect } from 'react';

import { useAppDispatch } from '@/store';
import { fetchHomeData } from '@/store/modules/home';
```

---

## 十、新增功能开发流程

以「带 Header 的首页 + 登录页」为例：

```
1. layouts/Main/、layouts/Auth/     搭布局壳子
2. components/AppHeader/              顶栏
3. router/routes.tsx                  嵌套路由绑定布局
4. views/Login/                       登录页（挂 AuthLayout）
5. service/auth.ts + store/modules/   需要时再加
6. mock/index.ts                      开发阶段 mock
```

**新增页面 checklist：**

- [ ] 页面放 `views/<Name>/`
- [ ] 路由注册在 `router/routes.tsx`
- [ ] 确认用 Main 还是 Auth 布局
- [ ] 数据走 `createAsyncThunk`，不直接调 service
- [ ] import 使用 `@/`
- [ ] **交付前自测**（见下）通过后再结束 / 提交

### 交付前自测（强制，AI / 人工均适用）

写完或改完代码后，**在宣告完成前必须自己跑通**，不要等浏览器 “Compiled with problems” 再修：

```bash
npm run lint
npm run typecheck   # 等价于 npx tsc --noEmit
```

| 命令 | 作用 |
| ---- | ---- |
| `npm run lint` | ESLint + Prettier |
| `npm run typecheck` | TypeScript 类型检查（与编译红字同源） |

涉及启动、路由、登录等行为时，按需再 `npm start` 冒烟。任一失败先修再交付。

---

## 十一、执行约定

1. **先归类再写文件** — 确认属于哪个目录再动手。
2. **拒绝「暂时放这」** — 不随意塞 `components/`。
3. **复用 ≥2 次才上提** — 从 `views/X/` 提到 `components/` 或 `hooks/`。
4. **布局与页面分离** — Header 在 layouts/components，不在 views。
5. **登录注册不走 Main** — 用 AuthLayout。
6. **请求走 service + store** — 页面只 dispatch + selector。
7. **写完必须自测** — `lint` + `typecheck` 通过后再说做完。
