import React, { Suspense } from 'react';
import { RouterProvider } from 'react-router-dom';
import router from './routes';

// 路由出口：使用 createBrowserRouter + RouterProvider（数据路由 API）
// routes.tsx 里已 createBrowserRouter，这里只需挂载 router 实例
function AppRouter() {
  return (
    // NotFound 用了 lazy()，懒加载期间显示 fallback
    <Suspense fallback={<div>加载中…</div>}>
      <RouterProvider router={router} />
    </Suspense>
  );
}

export default AppRouter;
