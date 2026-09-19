import React, { lazy } from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';

import RootLayout from '@/layouts/Root';
import MainLayout from '@/layouts/Main';
import HomeLayout from '@/layouts/Home';
import AuthGuard from '@/router/guards';
import AdminGuard from '@/router/AdminGuard';
import { preloadGameDetail, preloadPostDetail } from '@/router/preload';

// 同步引入首屏页面，保证首屏直出、无 loading 闪烁
import Home from '@/views/Home';
import Community from '@/views/Community';
import Games from '@/views/Games';
import Recommend from '@/views/Recommend';
import Profile from '@/views/Profile';
import Search from '@/views/Search';
import PostEditor from '@/views/PostEditor';
import Feed from '@/views/Feed';
import Notifications from '@/views/Notifications';
import Shop from '@/views/Shop';

// 次要页面懒加载，减小首屏 bundle 体积
const NotFound = lazy(() => import('@/views/NotFound'));
const PostDetail = lazy(preloadPostDetail);
const GameDetail = lazy(preloadGameDetail);
const AdminModeration = lazy(() => import('@/views/Admin/Moderation'));

// 用路由表创建 BrowserRouter 实例（数据路由 API）
const router = createBrowserRouter([
  {
    element: <RootLayout />,
    children: [
      // 官网首页：有顶栏，但无内容限宽盒，视频可全屏铺开
      {
        path: '/',
        element: <HomeLayout />,
        children: [{ index: true, element: <Home /> }],
      },
      // 兼容历史登录入口；登录能力由官网首页的全局弹窗提供。
      { path: '/login', element: <Navigate to="/" replace /> },
      {
        path: '/',
        element: <MainLayout />,
        children: [
          // 游客可读：社区最新帖、帖子详情等站内页面
          { path: 'community', element: <Community /> },
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
          // 他人主页通过帖子/头像公开访问；页面内的关注、举报、拉黑等写操作再单独鉴权。
          { path: 'profile', element: <Profile /> },
          { path: 'about', element: <Navigate to="/games" replace /> },
          {
            element: <AuthGuard />,
            children: [
              { path: 'feed', element: <Feed /> },
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
