import React, { memo } from 'react';
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
  } = useHeaderActions();

  return (
    <header className="app-header">
      <div className="app-header__inner">
        <HeaderBrand brand={headerBrand} />
        <HeaderNav items={headerNavItems} loggedIn={loggedIn} />
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
