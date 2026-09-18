# 前端架构、启动链路与部署

## 1. 运行时分层

前端采用“布局壳 + 页面 + 业务组件 + 基础 UI + service/store”分层。每一层有单向依赖，避免页面直接掌握请求、缓存和视觉细节。

```text
浏览器
  ↓
src/index.tsx
  ├─ 全局 LESS
  ├─ 开发环境 mock（按开关加载）
  └─ ReactDOM.createRoot
       ↓
     App
       ├─ Redux Provider
       ├─ ThemeProvider（light/dark + antd token）
       ├─ Antd ConfigProvider/App（zh-CN、反馈上下文）
       └─ RootLayout
            ├─ 全局 AppHeader / AuthModal / 进度条
            └─ RouterProvider
                 ├─ HomeLayout
                 └─ MainLayout → AuthGuard/AdminGuard → views
```

### 1.1 启动顺序

1. `src/index.tsx` 先引入 `bootstrap-clear-auth`，仅在开发环境或显式环境变量开启时清理一次旧登录态。
2. 开发环境且 mock 开关开启时加载 `src/mock/index.ts`，拦截指定接口；生产构建不会加载 mock。
3. `App` 装配 Redux、主题、Ant Design 和路由。
4. `ThemedAppContent` 启动跨标签登录同步、通知 SSE、资料审核轮询和未完成上传恢复。
5. `RootLayout` 负责全局 header、认证弹窗、文章进度条和 KeepAlive 相关容器；具体页面由路由决定。

## 2. 布局与宽度

| 布局 | 文件               | 责任                                   | 约束                                                                    |
| ---- | ------------------ | -------------------------------------- | ----------------------------------------------------------------------- |
| Root | `src/layouts/Root` | 全局 header、AuthModal、应用级状态容器 | 必须位于 Router 内，确保路由上下文可用                                  |
| Home | `src/layouts/Home` | 官网首页顶栏与全幅内容                 | 不使用站内限宽盒，视频背景覆盖 `calc(100vh - var(--app-header-height))` |
| Main | `src/layouts/Main` | 站内页顶栏、内容区、PageTools、刷新    | 使用 `--page-content-max-width: 960px` 和统一 gutter                    |

页面不写 Header，也不再私自设置 `max-width: 960px/1080px`。站内空白使用 `--color-bg-secondary`，卡片和内容盒使用 `--color-bg`。

## 3. 路由和页面加载

`src/router/routes.tsx` 是唯一的路由表，使用 `createBrowserRouter` 和嵌套路由：

```text
RootLayout
├── HomeLayout / → Home
└── MainLayout /
    ├── public: community, post/:id, game/:appId, recommend, games
    ├── AuthGuard: feed, profile, search, shop, notifications, post/editor
    └── AdminGuard: admin/moderation
```

首屏常用页面同步 import，详情页、404 和审核页 lazy import。`router/preload.ts` 在用户点击帖子/游戏卡片意图时预加载对应详情 chunk，点击后的导航仍以服务端详情为准。

页面级鉴权通过 `router/guards.tsx`，管理员鉴权通过 `router/AdminGuard.tsx`。组件内的单次操作鉴权使用 `useRequireLogin`，不能复制 token 检查。

## 4. 数据流与状态所有权

```text
view / component
    ↓ dispatch(thunk) 或 RTK Query hook
store / business hook
    ↓
service/*.ts
    ↓
HYRequest（token、refresh、错误归一化）
    ↓
gateway → 后端领域服务
```

状态按生命周期划分：

| 状态           | 位置                 | 例子                                       | 不应放置               |
| -------------- | -------------------- | ------------------------------------------ | ---------------------- |
| 跨页面业务状态 | `store/modules`      | auth、通知未读、文章审核进度、互动局部状态 | 组件内部或 URL         |
| 服务端列表缓存 | `store/services`     | community/follow infinite query            | 页面 `Map`、重复请求锁 |
| 页面临时状态   | view 或业务 hook     | 当前 tab、弹窗、输入框、裁剪框             | Redux 全局             |
| 纯展示转换     | `utils`/service 边界 | 数字、日期、后端枚举归一化                 | JSX 内重复实现         |

`serverApi` 使用 RTK Query infinite query 保存社区/关注流的页面；展示前通过 `flattenFeedPages` 去重。写操作成功后由 `updateQueryData`、刷新或失效标记通知列表，不同时维护第二份缓存。

## 5. 鉴权与请求生命周期

### 5.1 Token 策略

- access token 存 `localStorage`，请求层添加 `Authorization: Bearer ...`。
- refresh token 由后端以 HttpOnly Cookie 设置，前端只使用 `withCredentials`，不读取 refresh 值。
- 登录会话标志和 access 过期时间存 `localStorage`，用于启动检查和请求前刷新。
- mock 环境用同名普通 Cookie 模拟 refresh，不能当作生产安全方案。

### 5.2 刷新单飞

`src/service/request.ts` 用共享 `refreshPromise` 让同一标签页并发请求只发一次 refresh。以下情况会触发刷新：

1. 请求前发现 access 缺失或即将过期；
2. API 返回业务码 `40101`；
3. gateway 返回 HTTP 401 且不是 refresh 失效。

刷新成功后只重试原请求一次；refresh 失效或重试仍失败时清理登录态并发出 `auth-required` 事件，由认证 UI 决定提示和登录入口。登录/注册/refresh 请求通过 `skipAuth` 避免旧 token 干扰。

### 5.3 SSE

浏览器 `EventSource` 不能设置 Authorization Header，`notification.ts` 先复用请求层的 refresh，再按后端约定带 access token 建立 SSE。断线采用 hook 的重连策略；退出登录、refresh 失效或组件卸载时必须关闭连接。

## 6. 主题、样式和 Ant Design

`src/assets/css/common.less` 定义 light/dark CSS token；`useTheme` 同步 `data-theme` 与 antd `ConfigProvider` token。主色始终是 `#ff6600`，业务样式优先使用 `var(--*)`，不在页面散落魔法色。

Ant Design 统一为 6.5.1：

- `ConfigProvider` 提供中文 locale 和主题 token；
- `App.useApp()` 提供 message/notification/modal 上下文；
- `Form`、`Modal`、`Drawer`、`Table`、`Tabs`、`Upload` 等优先使用 antd 能力；
- 业务组件的局部样式放同目录 LESS，使用 BEM 命名；
- 不引入第二套 UI 库。

## 7. 性能与一致性策略

- 首屏页面同步导入，详情/审核/404 懒加载。
- 帖子列表统一 `ContentCard`，减少重复 DOM 和视觉漂移。
- Feed 用 RTK Query infinite query、主键去重、缓存刷新和预加载。
- 图片使用 LazyImage/封面组件；视频列表只显示封面，详情页再创建 DPlayer。
- 分片上传限制并发、支持重试、续传和中止；未完成会话由持久化恢复 hook 处理。
- 资料、通知和关注流通过 revision/dirty 标记失效，避免旧请求覆盖新状态。

## 8. 开发和生产配置

开发环境 `REACT_APP_BASE_URL` 留空时，`craco.config.js` 仅把 API 请求转发到 gateway，不把页面路由 `/game`、`/search` 等误代理；SSE 代理超时为 0。生产环境 `service/config.ts` 默认以当前站点为 API 根地址，也可用 `REACT_APP_BASE_URL` 指向独立 gateway。

生产静态服务 `scripts/serve-production.js`：

1. 访问 `/static/*` 和已有静态文件时直接从 `build/` 返回；
2. API 上下文转发到 `BACKEND_URL`，保留 query、Cookie、SSE 响应头；
3. 其他历史路由返回 `build/index.html`；
4. 后端不可用时返回明确的 502，而不是静默返回 HTML。

## 9. 架构级验证

```bash
npm run lint
npm run typecheck
npm run build
git diff --check
```

真实联调还需要后端仓库执行 `mvn -DskipTests compile`、`mvn test`，并验证 gateway 8080、Cookie/CORS、SSE、MinIO 分片上传和历史路由回退。前端构建通过只能证明静态依赖和类型正确，不能替代后端运行时验证。
