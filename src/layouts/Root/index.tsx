import React from 'react';
import { Outlet, ScrollRestoration } from 'react-router-dom';

import AppHeader from '@/components/AppHeader';
import ArticleProgressBanner from '@/components/ArticleProgressBanner';
import AuthModal from '@/components/AuthModal';
import { DecorationRegistryProvider } from '@/hooks/useDecorationRegistry';

import './style.less';

/** 根布局：全站顶栏 + 审核进度条 + 全局 AuthModal */
function RootLayout() {
  return (
    <DecorationRegistryProvider>
      <AppHeader />
      <ArticleProgressBanner />
      <div className="site-main">
        <Outlet />
      </div>
      <ScrollRestoration storageKey="gc:scroll-positions" />
      <AuthModal />
    </DecorationRegistryProvider>
  );
}

export default RootLayout;
