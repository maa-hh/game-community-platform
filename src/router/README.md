# router 路由说明

项目使用 React Router v7 数据路由。路由集中定义在 `src/router/routes.tsx`，`src/router/index.tsx` 只负责挂载 `RouterProvider`。

## 布局层级

```text
Root
├── 全局 AuthModalProvider / 鉴权兜底
└── Main
    ├── AppHeader
    └── 页面内容盒

Login
└── 登录相关页面与全屏背景
```

认证采用全局登录弹窗，不再使用独立认证路由布局。新增页面时先确认应挂在 `Main` 还是 `Home`，再在 `routes.tsx` 的对应 children 中注册。

## 路由约定

- 页面入口位于 `src/views/<Page>/index.tsx`，页面数据逻辑放在同目录 hook 或 service 层。
- 不在页面内散落 `<Route>` 或重复创建 Router。
- 需要登录的交互通过 `useRequireLogin`，不要复制 token 判断和跳转逻辑。
- 详情页通过 navigation state 传递可选预览数据，但最终以服务端详情为准。
- 页面刷新、返回和实时失效统一复用现有 `usePageRefresh`、RTK Query invalidation 与导航工具。
