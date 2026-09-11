import React, { memo, useLayoutEffect, useRef } from 'react';
import type { FC } from 'react';

import { useTheme } from '@/hooks/useTheme';

import {
  headerActions,
  headerBrand,
  headerNavItems,
  headerSearch,
} from './config';
import HeaderActions from './parts/HeaderActions';
import HeaderBrand from './parts/HeaderBrand';
import HeaderNav from './parts/HeaderNav';
import HeaderSearch from './parts/HeaderSearch';
import { useHeaderActions } from './useHeaderActions';

import './style.less';

const AppHeader: FC = () => {
  const headerRef = useRef<HTMLElement>(null);
  const { isDark, toggleMode } = useTheme();
  const {
    user,
    loggedIn,
    userMenu,
    userMenuOpen,
    setUserMenuOpen,
    goSearch,
    handlePublish,
    openLogin,
    openNotifications,
    notificationUnread,
    feedUnreadCount,
  } = useHeaderActions();

  useLayoutEffect(() => {
    const header = headerRef.current;
    if (!header) return undefined;

    const updateHeaderHeight = () => {
      document.documentElement.style.setProperty(
        '--app-header-height',
        `${header.getBoundingClientRect().height}px`,
      );
    };

    updateHeaderHeight();
    const observer = new ResizeObserver(updateHeaderHeight);
    observer.observe(header);

    return () => {
      observer.disconnect();
    };
  }, []);

  return (
    <header ref={headerRef} className="app-header">
      <div className="app-header__inner">
        <HeaderBrand brand={headerBrand} />
        <HeaderNav
          items={headerNavItems}
          loggedIn={loggedIn}
          feedUnreadCount={feedUnreadCount}
        />
        <HeaderSearch
          search={headerSearch}
          loggedIn={loggedIn}
          onSearch={goSearch}
        />
        <HeaderActions
          actions={headerActions}
          loggedIn={loggedIn}
          user={user}
          isDark={isDark}
          onToggleTheme={toggleMode}
          onPublish={handlePublish}
          onLogin={openLogin}
          onOpenNotifications={openNotifications}
          notificationUnread={notificationUnread}
          userMenu={userMenu}
          userMenuOpen={userMenuOpen}
          onUserMenuOpenChange={setUserMenuOpen}
        />
      </div>
    </header>
  );
};

export default memo(AppHeader);
