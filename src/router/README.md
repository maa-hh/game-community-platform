# router 路由说明

项目使用 React Router v7 数据路由。`src/router/routes.tsx` 是唯一路由表，`src/router/index.tsx` 只负责挂载 `RouterProvider` 和 lazy fallback；页面目录不能自行创建 `<BrowserRouter>` 或散落 `<Route>`。

## 布局层级

```text
RootLayout
├── 全局状态副作用（SSE、资料审核轮询、上传恢复）
├── AppHeader / AuthModal / ArticleProgressBanner
└── children
    ├── HomeLayout → /
    └── MainLayout → 站内内容盒
        ├── 游客路由
        └── AuthGuard → 登录路由 → AdminGuard → 管理员路由
```

登录和注册是全局 `AuthModal`，不是独立 Login layout。`/login` 仅用于兼容旧链接并重定向到 `/`。

## 当前路由表

| 路径                  | 页面                     | 加载           | 守卫                                     |
| --------------------- | ------------------------ | -------------- | ---------------------------------------- |
| `/`                   | `views/Home`             | 同步           | 无                                       |
| `/community`          | `views/Community`        | 同步           | 无                                       |
| `/post/:id`           | `views/PostDetail`       | lazy + preload | 无                                       |
| `/game/:appId`        | `views/GameDetail`       | lazy + preload | 无                                       |
| `/recommend`          | `views/Recommend`        | 同步           | 无                                       |
| `/games`              | `views/Games`            | 同步           | 无                                       |
| `/feed`               | `views/Feed`             | 同步           | 游客可进入，内容需登录                   |
| `/profile?accountId=` | `views/Profile`          | 同步           | 无（他人主页公开；本人私有操作单独鉴权） |
| `/search`             | `views/Search`           | 同步           | `AuthGuard`                              |
| `/shop`               | `views/Shop`             | 同步           | 游客可浏览商品，兑换需登录               |
| `/notifications`      | `views/Notifications`    | 同步           | `AuthGuard`                              |
| `/post/editor`        | `views/PostEditor`       | 同步           | `AuthGuard`                              |
| `/admin/moderation`   | `views/Admin/Moderation` | lazy           | `AuthGuard` + `AdminGuard`               |
| `*`                   | `views/NotFound`         | lazy           | 无                                       |

`/about` 重定向到 `/games`。详情页和编辑页标记 `disableKeepAlive`，避免播放器、上传任务或大表单在切换路由后继续占用资源。

## 新增/修改路由流程

1. 在 `views/<Page>/index.tsx` 创建页面，页面不要负责布局壳。
2. 判断页面属于 Home（全幅）还是 Main（960px 内容盒）。
3. 在 `routes.tsx` 对应 children 注册，决定同步 import 或 `lazy()`。
4. 需要整页登录的页面放入 `AuthGuard`；允许游客进入但操作需登录的页面，在操作边界使用 `useRequireLogin`。
5. 详情卡片如有预览数据，只通过 navigation state 作为首屏提示，页面必须请求服务端最终数据。
6. 运行 lint/typecheck/build，并验证直接刷新目标历史 URL。

## 鉴权和刷新

- 页面级鉴权由 `guards.tsx` 判断本地会话并打开全局登录弹窗；公开的他人主页通过
  `/profile?accountId=` 进入，不应被整体登录守卫拦截，本人私有 Tab 和写操作再单独鉴权。
- 单次按钮/写操作使用 `useRequireLogin`，不在组件复制 token 判断。
- 刷新、返回和实时失效分别复用 `usePageRefresh`、导航工具、RTK Query invalidation 和 `profileRealtime`。
- 认证状态失效由 service 发事件，不在路由层直接显示 Toast。
