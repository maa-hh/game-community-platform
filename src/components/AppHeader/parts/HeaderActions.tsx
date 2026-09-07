import React, { memo } from 'react';
import type { FC } from 'react';
import UserAvatarWithFrame from '@/base-ui/UserAvatarWithFrame';
import { Badge, Button, Dropdown, Space } from 'antd';
import type { MenuProps } from 'antd';
import {
  DownOutlined,
  MailOutlined,
  MoonOutlined,
  SunOutlined,
  UpOutlined,
} from '@ant-design/icons';

import type { IUserInfo } from '@/service/types';
import { useDecorationRegistry } from '@/hooks/useDecorationRegistry';
import { resolveAvatarFrameAsset } from '@/constants/avatarFrameCatalog';

import type { HeaderActionsConfig } from '../types';

interface HeaderActionsProps {
  actions: HeaderActionsConfig;
  loggedIn: boolean;
  user: IUserInfo | null;
  isDark: boolean;
  onToggleTheme: () => void;
  onPublish: () => void;
  onLogin: () => void;
  onOpenNotifications?: () => void;
  notificationUnread?: number;
  userMenu: MenuProps['items'];
  userMenuOpen: boolean;
  onUserMenuOpenChange: (open: boolean) => void;
}

const HeaderActions: FC<HeaderActionsProps> = ({
  actions,
  loggedIn,
  user,
  isDark,
  onToggleTheme,
  onPublish,
  onLogin,
  onOpenNotifications,
  notificationUnread = 0,
  userMenu,
  userMenuOpen,
  onUserMenuOpenChange,
}) => {
  const decoration = useDecorationRegistry(user?.accountId);
  const hasAvatarFrame = Boolean(
    resolveAvatarFrameAsset(
      decoration?.avatarFrame?.code,
      decoration?.avatarFrame?.assetJson,
    )?.frameUrl,
  );

  return (
    <Space size={12} className="app-header__actions">
      <Button
        type="text"
        className="app-header__theme-btn"
        aria-label={isDark ? '切换浅色模式' : '切换深色模式'}
        icon={isDark ? <SunOutlined /> : <MoonOutlined />}
        onClick={onToggleTheme}
      />

      <Button
        type="primary"
        className="app-header__publish-btn"
        onClick={onPublish}
      >
        {actions.publishLabel}
      </Button>

      {loggedIn ? (
        <>
          <Badge
            className="app-header__notification-badge"
            count={notificationUnread}
            overflowCount={99}
            offset={[-4, 3]}
          >
            <Button
              type="text"
              className="app-header__mail-btn"
              aria-label="消息通知"
              icon={<MailOutlined />}
              onClick={onOpenNotifications}
            />
          </Badge>
          <Dropdown
            menu={{ items: userMenu }}
            placement="bottomRight"
            trigger={['click']}
            open={userMenuOpen}
            onOpenChange={onUserMenuOpenChange}
          >
            <button type="button" className="app-header__avatar-btn">
              <span
                className={`app-header__avatar-wrap${
                  hasAvatarFrame ? ' app-header__avatar-wrap--framed' : ''
                }`}
              >
                <UserAvatarWithFrame
                  accountId={user?.accountId}
                  name={user?.username || user?.email || 'U'}
                  src={user?.avatar}
                  size={36}
                />
              </span>
              {userMenuOpen ? (
                <UpOutlined className="app-header__avatar-caret" />
              ) : (
                <DownOutlined className="app-header__avatar-caret" />
              )}
            </button>
          </Dropdown>
        </>
      ) : (
        <Button
          type="primary"
          className="app-header__login-btn"
          onClick={onLogin}
        >
          {actions.loginLabel}
        </Button>
      )}
    </Space>
  );
};

export default memo(HeaderActions);
