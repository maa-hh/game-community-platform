import {
  createElement,
  useCallback,
  useEffect,
  useMemo,
  useState,
} from 'react';
import { useNavigate } from 'react-router-dom';
import type { MenuProps } from 'antd';
import {
  LogoutOutlined,
  ShoppingOutlined,
  UserOutlined,
} from '@ant-design/icons';

import { useAppDispatch, useAppSelector } from '@/store';
import { logoutAction } from '@/store/modules/auth';
import { fetchNotificationMetaAction } from '@/store/modules/notification';
import { useAuthModal } from '@/hooks/useAuthModal';
import { isAuthenticated } from '@/utils/storage';
import { addSearchHistoryApi } from '@/service/search';

import { headerActions, headerSearch, userMenuSchema } from './config';
import { isUserMenuDivider } from './types';

const USER_MENU_ICONS = {
  profile: UserOutlined,
  shop: ShoppingOutlined,
  logout: LogoutOutlined,
} as const;

export function useHeaderActions() {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { user } = useAppSelector((state) => state.auth);
  const notificationUnread = useAppSelector(
    (state) => state.notification.summary.unreadNotificationCount,
  );
  const { openAuth } = useAuthModal();
  const loggedIn = isAuthenticated();
  const [userMenuOpen, setUserMenuOpen] = useState(false);

  useEffect(() => {
    if (!loggedIn) return;
    void dispatch(fetchNotificationMetaAction());
  }, [dispatch, loggedIn, user?.accountId]);

  const goSearch = useCallback(
    (keyword: string) => {
      const q = keyword.trim();
      if (!q) return;
      if (loggedIn) {
        void addSearchHistoryApi(q).catch(() => undefined);
      }
      navigate(
        `/search?q=${encodeURIComponent(q)}&tab=${headerSearch.defaultTab}`,
      );
    },
    [loggedIn, navigate],
  );

  const handlePublish = useCallback(() => {
    if (!loggedIn) {
      openAuth('login');
      return;
    }
    navigate(headerActions.publishTo);
  }, [loggedIn, navigate, openAuth]);

  const openLogin = useCallback(() => {
    openAuth('login');
  }, [openAuth]);

  const openNotifications = useCallback(() => {
    if (!loggedIn) {
      openAuth('login');
      return;
    }
    navigate('/notifications');
  }, [loggedIn, navigate, openAuth]);

  const userMenu = useMemo<MenuProps['items']>(
    () =>
      userMenuSchema.map((item) => {
        if (isUserMenuDivider(item)) {
          return { type: 'divider' as const };
        }

        const Icon = USER_MENU_ICONS[item.icon];
        const iconNode = createElement(Icon);

        if (item.key === 'profile') {
          return {
            key: item.key,
            icon: iconNode,
            label: item.label,
            onClick: () => navigate('/profile'),
          };
        }

        if (item.key === 'shop') {
          return {
            key: item.key,
            icon: iconNode,
            label: item.label,
            onClick: () => navigate('/shop'),
          };
        }

        return {
          key: item.key,
          icon: iconNode,
          label: item.label,
          onClick: async () => {
            await dispatch(logoutAction());
            navigate('/login', { replace: true });
          },
        };
      }),
    [dispatch, navigate],
  );

  return {
    user,
    loggedIn,
    userMenu,
    userMenuOpen,
    setUserMenuOpen,
    notificationUnread,
    goSearch,
    handlePublish,
    openLogin,
    openNotifications,
  };
}
