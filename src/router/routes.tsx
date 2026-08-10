import React, { lazy } from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';

import RootLayout from '@/layouts/Root';
import MainLayout from '@/layouts/Main';
import LoginLayout from '@/layouts/Login';
import AuthGuard from '@/router/guards';
import AdminGuard from '@/router/AdminGuard';

// 同步引入首屏页面，保证首屏直出、无 loading 闪烁
import Home from '@/views/Home';
import Games from '@/views/Games';
import Recommend from '@/views/Recommend';
import Login from '@/views/Login';
import Profile from '@/views/Profile';
import Search from '@/views/Search';
import PostEditor from '@/views/PostEditor';
import Feed from '@/views/Feed';
import Notifications from '@/views/Notifications';
import Shop from '@/views/Shop';

// 次要页面懒加载，减小首屏 bundle 体积
const NotFound = lazy(() => import('@/views/NotFound'));
const PostDetail = lazy(() => import('@/views/PostDetail'));
const GameDetail = lazy(() => import('@/views/GameDetail'));
const AdminModeration = lazy(() => import('@/views/Admin/Moderation'));

// 用路由表创建 BrowserRouter 实例（数据路由 API）
const router = createBrowserRouter([
  {
    element: <RootLayout />,
    children: [
      // 登录落地页：有顶栏，但无内容限宽盒，视频可全屏铺开
      {
        path: '/login',
        element: <LoginLayout />,
        children: [{ index: true, element: <Login /> }],
      },
      {
        path: '/',
        element: <MainLayout />,
        children: [
          // 游客可读：首页最新帖、帖子详情 index默认挂载子页面
          { index: true, element: <Home /> },
          {
            path: 'post/:id',
            element: <PostDetail />,
            handle: { disableKeepAlive: true },
          },
          {
            path: 'game/:appId',
            element: <GameDetail />,
            handle: { disableKeepAlive: true },
          },
          { path: 'recommend', element: <Recommend /> },
          { path: 'games', element: <Games /> },
          { path: 'about', element: <Navigate to="/games" replace /> },
          {
            element: <AuthGuard />,
            children: [
              { path: 'feed', element: <Feed /> },
              { path: 'profile', element: <Profile /> },
              { path: 'search', element: <Search /> },
              { path: 'shop', element: <Shop /> },
              { path: 'notifications', element: <Notifications /> },
              {
                path: 'post/editor',
                element: <PostEditor />,
                handle: { disableKeepAlive: true },
              },
              {
                element: <AdminGuard />,
                children: [
                  {
                    path: 'admin/moderation',
                    element: <AdminModeration />,
                    handle: { disableKeepAlive: true },
                  },
                ],
              },
            ],
          },
          {
            path: '*',
            element: <NotFound />,
            handle: { disableKeepAlive: true },
          },
        ],
      },
    ],
  },
]);

export default router;
