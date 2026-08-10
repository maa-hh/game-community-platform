# 路由配置说明

本目录集中管理项目路由。当前项目使用 **数据路由 API** + **History 模式**。

```
src/router/
├── routes.tsx   # 路由表 + createBrowserRouter 创建 router 实例
├── index.tsx    # 路由出口，挂载 RouterProvider
└── README.md    # 本文档
```

---

## 一、路由 API 的两种写法

React Router v6 提供两套 API，都能实现页面跳转，但组织方式不同。

### 对比总览

| 对比项     | 组件式路由                           | 数据路由（当前项目）                     |
| ---------- | ------------------------------------ | ---------------------------------------- |
| 核心 API   | `BrowserRouter` + `Routes` + `Route` | `createBrowserRouter` + `RouterProvider` |
| 路由定义   | 在 JSX 里写 `<Route>`                | 在 JS 配置对象/数组里定义                |
| 路由出口   | `index.tsx` 里 map 生成 Route        | `index.tsx` 只挂 `<RouterProvider>`      |
| 数据预加载 | ❌ 不支持 loader                     | ✅ 支持 `loader` / `action`              |
| 错误边界   | 需自己处理                           | ✅ 支持 `errorElement`                   |
| 适合场景   | 小项目、路由简单                     | 中大型项目、需要路由级数据加载           |
| 直观程度   | 更直观，看到的就是 JSX 结构          | 配置与渲染分离，扩展性更强               |

---

### 写法 A：组件式路由

路由用 JSX 组件描述，路由表只是数据源，还要在 `index.tsx` 里手动 map 成 `<Route>`。

**routes.tsx**

```tsx
import Home from '@/views/Home';
import About from '@/views/About';
import NotFound from '@/views/NotFound';

export const routes = [
  { path: '/', name: 'home', element: <Home /> },
  { path: '/about', name: 'about', element: <About /> },
  { path: '*', name: 'not-found', element: <NotFound /> },
];
```

**index.tsx**

```tsx
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { routes } from './routes';

function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        {/* 方式 1：map 自动生成 */}
        {routes.map((route) => (
          <Route key={route.path} path={route.path} element={route.element} />
        ))}

        {/* 方式 2：也可以手写，效果一样 */}
        {/* <Route path="/" element={<Home />} /> */}
        {/* <Route path="/about" element={<About />} /> */}
        {/* <Route path="*" element={<NotFound />} /> */}
      </Routes>
    </BrowserRouter>
  );
}
```

**特点：**

- 路由就是 JSX，写在 render 函数里，一眼能看懂页面结构
- `routes.map()` 不是必须的，路由少时可以直接手写 `<Route>`
- 不支持 `loader`，页面数据要在组件里用 `useEffect` 自己请求

---

### 写法 B：数据路由（✅ 当前项目使用）

路由先写成配置对象，用 `createBrowserRouter` 生成 router 实例，再用 `RouterProvider` 挂载。

**routes.tsx**

```tsx
import { createBrowserRouter } from 'react-router-dom';
import Home from '@/views/Home';
import About from '@/views/About';
import NotFound from '@/views/NotFound';

export const routes = [
  { path: '/', name: 'home', element: <Home /> },
  { path: '/about', name: 'about', element: <About /> },
  { path: '*', name: 'not-found', element: <NotFound /> },
];

// 用路由表创建 router 实例
const router = createBrowserRouter(
  routes.map(({ path, element }) => ({ path, element })),
);

export default router;
```

**index.tsx**

```tsx
import { RouterProvider } from 'react-router-dom';
import router from './routes';

function AppRouter() {
  return <RouterProvider router={router} />;
}
```

**特点：**

- `index.tsx` 更简洁，不再手写 `<Routes>` / `<Route>`
- 路由配置与渲染分离，便于维护和测试
- 支持高级能力（当前未用，但可随时扩展）：

```tsx
// 数据路由独有：进页面前预加载数据
{
  path: '/game/:id',
  element: <GameDetail />,
  loader: async ({ params }) => {
    // 页面渲染前就把数据准备好
    const res = await fetch(`/api/games/${params.id}`);
    return res.json();
  },
  // 组件里通过 useLoaderData() 直接拿数据，无需 useEffect
  errorElement: <ErrorPage />,  // 加载失败时显示错误页
}
```

---

### 两种写法怎么选？

| 你的情况                                     | 建议                         |
| -------------------------------------------- | ---------------------------- |
| 只有几个展示页，无复杂数据请求               | 组件式够用，更直观           |
| 需要在进页面前拉数据（loader）               | 数据路由                     |
| 需要嵌套布局（如后台侧边栏 + 内容区）        | 数据路由的 `children` 更清晰 |
| 当前项目（游戏社区，后续会有详情页数据加载） | ✅ 数据路由更合适            |

---

### 补充：`component` 与 `element` 的区别

很多人初学路由时会困惑：路由配置里为什么写 `element={<Home />}`，而不是 `component={Home}`？

#### 一句话区别

|              | `component`                            | `element`                        |
| ------------ | -------------------------------------- | -------------------------------- |
| 所属版本     | React Router **v5 及以前**             | React Router **v6+**（当前项目） |
| 传什么       | 传**组件本身**（函数/类）              | 传**已经写好的 JSX 元素**        |
| 现在还能用吗 | v6 的 `<Route>` **已移除** `component` | ✅ 当前标准写法                  |

#### `component` — 旧写法（v5）

把组件**函数**传给路由，由 Router **自己帮你实例化**：

```tsx
// React Router v5
import Home from '@/views/Home';

<Route path="/" component={Home} />;
// Router 内部等价于：React.createElement(Home)
```

注意：传的是组件类型，不是 JSX：

```tsx
<Route path="/" component={Home} />        // ✅ 正确
<Route path="/" component={<Home />} />    // ❌ 错误
```

#### `element` — 新写法（v6，当前项目）

把**已经构造好的 React 元素（JSX）** 传给路由：

```tsx
// React Router v6 — 当前 routes.tsx 的写法
import Home from '@/views/Home';

<Route path="/" element={<Home />} />

// 路由表配置对象里：
{
  path: '/',
  element: <Home />,   // ✅ v6 正确
}

// 如果写成 component 会报错：
{
  path: '/',
  component: Home,     // ❌ v6 不支持
}
```

`createBrowserRouter` 的配置对象里也只有 `element`，没有 `component`。

#### 为什么 v6 改成 `element`？

`element` 更灵活，可以在路由层就包好布局、传 props：

```tsx
// v6：路由层直接包 Layout、传 props
{
  path: '/about',
  element: (
    <Layout>
      <About title="关于我们" />
    </Layout>
  ),
}

// v5 的 component 做不到这么直接，通常要用 render：
<Route
  path="/about"
  render={() => (
    <Layout>
      <About title="关于我们" />
    </Layout>
  )}
/>
```

v6 用 `element` 统一了 v5 的 `component` 和 `render` 两种旧写法。

#### 和"跳转"的关系

`component` / `element` **不负责跳转**，只负责"匹配到路径后渲染什么页面"。

跳转靠的是：

```tsx
import { Link, useNavigate } from 'react-router-dom';

// 声明式跳转（推荐，如 <Link to="/about">）
<Link to="/about">去关于页</Link>;

// 编程式跳转（如按钮 onClick）
const navigate = useNavigate();
navigate('/about');
```

完整流程：

```
Link / navigate 改变 URL
    → Router 匹配 path
    → 找到对应 route 的 element
    → 渲染 <About />
```

> **总结：`component` 是 v5 旧 API，传组件函数；`element` 是 v6 新 API，传 JSX 元素。当前项目只用 `element`。跳转用 `Link` 或 `useNavigate`，和 `element` 是两套东西。**

---

## 二、路由模式的两种写法

除了 API 写法，路由还有 **URL 模式** 的区别：History 模式 vs Hash 模式。

### 对比总览

| 对比项             | History 模式（✅ 当前）                                  | Hash 模式                              |
| ------------------ | -------------------------------------------------------- | -------------------------------------- |
| URL 形态           | `http://localhost:3000/about`                            | `http://localhost:3000/#/about`        |
| 原理               | 使用 HTML5 `history.pushState`                           | 使用 URL 的 `#` 片段（hash）           |
| 是否需要服务端配置 | ✅ 需要（见下方说明）                                    | ❌ 不需要                              |
| SEO 友好度         | 较好                                                     | 较差（`#` 后内容搜索引擎一般不索引）   |
| 兼容性             | 现代浏览器                                               | 所有浏览器（含 IE）                    |
| 刷新页面           | 浏览器向服务器请求 `/about`，需服务端回退到 `index.html` | 浏览器只请求 `/`，`#` 后内容由前端处理 |
| React Router API   | `createBrowserRouter` / `BrowserRouter`                  | `createHashRouter` / `HashRouter`      |

---

### 深入理解：History 与 Hash 到底差在哪？

先忘掉代码，只看 URL 长什么样：

| 页面     | History 模式 URL   | Hash 模式 URL        |
| -------- | ------------------ | -------------------- |
| 首页     | `game.com/`        | `game.com/#/`        |
| 关于     | `game.com/about`   | `game.com/#/about`   |
| 游戏详情 | `game.com/game/42` | `game.com/#/game/42` |

**History：路径写在域名后面，干净。**
**Hash：中间有个 `#`，路径写在 `#` 后面。**

#### `#` 到底是什么？

浏览器里 URL 分两段理解：

```
http://game.com/about
└──────┬──────┘└─┬──┘
     域名+协议    路径（会发给服务器）

http://game.com/#/about
└──────┬──────┘ └┬┘ └─┬──┘
     域名+协议   #   hash 段（不会发给服务器）
```

**关键：`#` 后面的内容，浏览器不会发给服务器。**

- 访问 `game.com/#/about` 时，服务器只收到请求：`game.com/`
- `/#/about` 完全是浏览器本地的事，由前端 JS 处理

#### 用"餐厅前台"来理解

把**服务器**想成餐厅前台，**前端路由**想成你自己的导航 App。

**History 模式：**

你对前台说："我要 `/about` 这份菜。"

前台查菜单：有 `/about` 就给，**没有就 404**。

但 SPA 其实只有一个菜：`index.html`。`/about`、`/game/42` 都是前端路由**虚拟出来的路径**，服务器上并没有这些文件。

所以 History 模式要求前台配置：

> "不管客人点什么路径，都先给他 `index.html`，剩下的让前端 App 自己处理。"

这就是 Nginx 里 `try_files ... /index.html` 的作用。

**Hash 模式：**

你对前台说："我要 `/`。"（`#` 后面的话你不跟前台说）

前台永远只给你 `index.html`，然后你自己看导航 App 里的 `#/about`，切到关于页。

**不需要前台配合，部署到任何地方都能用。**

#### 三个关键场景

**场景 1：在网站内点链接跳转**

```
History：/  →  /about      （地址栏变了，页面切换，不刷新）✅
Hash：   /#/  →  /#/about   （一样，不刷新）✅
```

两种都没问题，体验几乎一样。

**场景 2：刷新页面（F5）—— 这是核心差别**

History 模式 — 你在 `game.com/about` 按 F5：

```
浏览器 → 服务器：请给我 /about 这个文件
服务器：没有 about 这个文件 → 404 ❌
```

除非服务器配置了"所有路径都返回 index.html"。

Hash 模式 — 你在 `game.com/#/about` 按 F5：

```
浏览器 → 服务器：请给我 / 这个文件（# 后面不发）
服务器：给你 index.html ✅
浏览器：拿到 index.html，前端路由看到 #/about，显示关于页 ✅
```

**场景 3：把链接发给别人**

- History：`game.com/game/42` — 好看，像真网页
- Hash：`game.com/#/game/42` — 有 `#`，略丑

#### 一图总结

```
                    History 模式              Hash 模式
                    ─────────────             ──────────
URL 长相            game.com/about            game.com/#/about
                    干净 ✅                    有 # ⚠️

站内跳转            正常 ✅                    正常 ✅

刷新页面            服务器要找 /about 文件      服务器只要 /
                    没有就 404 ❌              永远给 index.html ✅

部署难度            需要服务器配置             丢到静态服务器就行 ✅

SEO                 较好 ✅                    # 后内容难被收录 ❌
```

> **最后一句话：History = URL 干净，但刷新时浏览器会跟服务器要真实路径，需要服务器配合。Hash = URL 带 `#`，刷新时服务器永远只要首页，前端自己看 `#` 后面决定显示什么。站内跳转两种都一样，真正差别在刷新和直接访问 URL 时浏览器跟服务器说了什么。**

---

### 模式 A：History 模式（✅ 当前项目）

URL 干净，没有 `#`，是大多数现代 Web 应用的选择。

```
http://localhost:3000/          → 首页
http://localhost:3000/about     → 关于页
http://localhost:3000/game/42  → 游戏详情（未来）
```

**当前代码：**

```tsx
// routes.tsx
import { createBrowserRouter } from 'react-router-dom';
const router = createBrowserRouter(routes);
```

```tsx
// 组件式等价写法
import { BrowserRouter } from 'react-router-dom';
<BrowserRouter>...</BrowserRouter>;
```

**⚠️ 部署注意：**

History 模式下，用户直接访问 `http://yoursite.com/about` 或刷新页面时，浏览器会向服务器请求 `/about` 这个路径。如果服务器没有对应文件，会返回 404。

**解决方案：** 配置服务器把所有路径都回退到 `index.html`：

```nginx
# Nginx 配置示例
location / {
  try_files $uri $uri/ /index.html;
}
```

开发环境（`npm start`）CRA 已内置此配置，无需担心。生产部署时需要配置。

---

### 模式 B：Hash 模式

URL 带 `#`，`#` 后面的路径由前端路由处理，服务器始终只收到 `/` 的请求。

```
http://localhost:3000/#/          → 首页
http://localhost:3000/#/about     → 关于页
http://localhost:3000/#/game/42  → 游戏详情
```

**如何切换（只需改 routes.tsx 一行）：**

```tsx
// 数据路由：把 createBrowserRouter 换成 createHashRouter
import { createHashRouter } from 'react-router-dom';

const router = createHashRouter(
  routes.map(({ path, element }) => ({ path, element })),
);
```

```tsx
// 组件式等价写法
import { HashRouter } from 'react-router-dom';
<HashRouter>...</HashRouter>;
```

**特点：**

- 不需要服务端配置，部署到任何静态服务器（GitHub Pages、OSS）都能直接用
- URL 有 `#`，不够美观
- 不适合需要 SEO 的页面

---

### 两种模式怎么选？

| 你的情况                          | 建议                    |
| --------------------------------- | ----------------------- |
| 有自己的服务器，能配 Nginx/Apache | ✅ History 模式（当前） |
| 部署到 GitHub Pages、纯静态 OSS   | Hash 模式更省事         |
| 需要 SEO（游戏社区可能需要）      | ✅ History 模式         |
| 内网系统、不需要 SEO              | 两种都行                |
| 当前项目（游戏社区）              | ✅ History 模式         |

---

## 三、当前项目完整结构

```
用户访问 URL
    │
    ▼
BrowserRouter（History 模式，URL 无 #）
    │
    ▼
RouterProvider（数据路由 API，挂载 router 实例）
    │
    ▼
routes 路由表匹配 path
    │
    ├─ /        → views/Home
    ├─ /about   → views/About
    └─ *        → views/NotFound（lazy 懒加载）
```

**文件职责：**

| 文件         | 职责                                                                   |
| ------------ | ---------------------------------------------------------------------- |
| `routes.tsx` | 定义路由表 `routes[]`，用 `createBrowserRouter` 创建并导出 router 实例 |
| `index.tsx`  | 引入 router，用 `<RouterProvider>` 挂载，包裹 `<Suspense>` 处理懒加载  |
| `App.tsx`    | 根组件，只渲染 `<AppRouter />`                                         |

---

## 四、新增页面流程

无论用哪种 API 写法，新增页面都是 3 步：

### 1. 创建页面组件

```
src/views/GameDetail/index.tsx
```

### 2. 在 routes.tsx 注册

```tsx
import GameDetail from '@/views/GameDetail';

export const routes: AppRoute[] = [
  // ...已有路由
  {
    path: '/game/:id', // :id 是动态参数
    name: 'game-detail',
    element: <GameDetail />,
  },
];
```

如果用了 `createBrowserRouter`，记得在 map 里新路由会自动包含进去（当前写法已覆盖）。

### 3. 页面内获取路由参数（如有动态段）

```tsx
import { useParams } from 'react-router-dom';

function GameDetail() {
  const { id } = useParams(); // 拿到 URL 中的 :id
  return <div>游戏 ID：{id}</div>;
}
```

**不要在组件里散落 `<Route>`，路由统一在 `router/` 管理。**

---

## 五、懒加载、分包与 Suspense

### 5.1 分包（Code Splitting）是什么

**分包**就是把打包产物从「一个大 JS 文件」拆成「多个小文件（chunk）」。

```
打包前（源码）                    打包后（浏览器下载）
─────────────                    ──────────────────
views/Home/                      main.js        ← 首屏必需
views/About/          ──打包──▶  669.chunk.js   ← 懒加载页面
views/NotFound/                  045.chunk.js   ← 懒加载页面
```

`npm run build` 后可以看到：

```
main.83e10aec.js      77 kB   ← 主包（首屏）
669.045a9ab3.chunk.js  309 B   ← 懒加载 chunk
```

**作用：** 减小首屏体积、按需加载、独立缓存。

---

### 5.2 路由懒加载是什么

**路由懒加载** = 访问某个路由时，才去下载对应页面的 JS。

当前 `routes.tsx` 中的写法：

```tsx
// 同步引入 — 打包进 main.js，首屏就下载
import Home from '@/views/Home';
import About from '@/views/About';

// 懒加载 — 打成独立 chunk，访问时才下载
const NotFound = lazy(() => import('@/views/NotFound'));
```

```
用户访问 /about  → 只下载 main.js + About 代码
用户访问 /xxx    → 此时才下载 NotFound chunk
```

### 什么时候用？什么时候不用？

| ✅ 适合懒加载        | ❌ 不适合懒加载      |
| -------------------- | -------------------- |
| 404 页               | 首页（用户必看）     |
| 后台管理页           | 登录页               |
| 详情页（路径多）     | 很小的页面（几十行） |
| 大表单、图表、富文本 | 全局布局、导航栏     |

当前项目做法（合理）：

```tsx
import Home from '@/views/Home'; // 同步 — 首屏直出
import About from '@/views/About'; // 同步 — 常用页
const NotFound = lazy(() => import('@/views/NotFound')); // 懒加载 — 很少访问
```

---

### 5.3 `<Suspense>` 是什么？没它懒加载能用吗？

`<Suspense>` 是 React 提供的组件：**子组件还没准备好时，先显示 fallback 占位内容。**

```tsx
// index.tsx
<Suspense fallback={<div>加载中…</div>}>
  <RouterProvider router={router} />
</Suspense>
```

#### 和 lazy() 的关系

```
lazy() 负责"什么时候加载"（访问时才下载 JS）
Suspense 负责"加载期间显示什么"（显示 fallback）
```

#### 没 Suspense 能用 lazy 吗？

**用了 `lazy()` 就必须有 `<Suspense>`**，否则容易报错：

```
A component suspended while responding to synchronous input.
This Suspense boundary was not provided with a fallback.
```

| 情况                         | 结果                         |
| ---------------------------- | ---------------------------- |
| `lazy()` + 有 `<Suspense>`   | ✅ 正常，下载时显示 fallback |
| `lazy()` + 没有 `<Suspense>` | ❌ 报错或白屏                |
| 不用 `lazy()`，普通 `import` | ✅ 不需要 `<Suspense>`       |

#### 完整流程

```
用户访问不存在的路径
    1. Router 匹配 path: '*'，要渲染 <NotFound />
    2. NotFound 是 lazy 组件，JS 还没下载完 → 组件"挂起"
    3. Suspense 捕获 → 显示「加载中…」
    4. NotFound.js 下载完成 → 显示 404 页面
```

#### 常见误区

- **一个 Suspense 可以包住所有懒加载路由**，不需要每个页面单独包
- **同步引入的页面不会触发 fallback**（代码已在 main.js 里）
- **fallback 可以是骨架屏、转圈动画**，不只是文字

#### 三种写法对比

```tsx
// 写法 1：全同步 — 最简单，不需要 Suspense
import Home from '@/views/Home';
<RouterProvider router={router} />;

// 写法 2：部分懒加载 — 当前项目（推荐）
const NotFound = lazy(() => import('@/views/NotFound'));
<Suspense fallback={<div>加载中…</div>}>
  <RouterProvider router={router} />
</Suspense>;

// 写法 3：全懒加载 — 不推荐，首屏也会 loading
const Home = lazy(() => import('@/views/Home'));
<Suspense fallback={<div>加载中…</div>}>
  <RouterProvider router={router} />
</Suspense>;
```

> **总结：分包是打包结果，懒加载是代码写法，Suspense 是加载期间的兜底。`lazy()` 和 `<Suspense>` 是搭档，有 lazy 就要有 Suspense。**

---

## 六、快速切换参考

### 从数据路由切回组件式

```tsx
// index.tsx 改为：
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { routes } from './routes';

function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        {routes.map((route) => (
          <Route key={route.path} path={route.path} element={route.element} />
        ))}
      </Routes>
    </BrowserRouter>
  );
}
```

```tsx
// routes.tsx 去掉 createBrowserRouter，只导出 routes 数组
export const routes = [...];
```

### 从 History 切到 Hash

```tsx
// routes.tsx 只改这一行：
import { createHashRouter } from 'react-router-dom';
const router = createHashRouter(routes.map(...));
```

`index.tsx` 无需改动。

---

## 七、相关文件

| 文件                   | 位置         | 说明                         |
| ---------------------- | ------------ | ---------------------------- |
| `src/App.tsx`          | `src/`       | 根组件，渲染 `<AppRouter />` |
| `src/views/`           | `src/views/` | 页面级组件，每个路由对应一个 |
| `PROJECT_STRUCTURE.md` | 项目根目录   | 全项目目录规范               |
