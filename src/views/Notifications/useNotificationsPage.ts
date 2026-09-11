import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { App } from 'antd';

import { useAppDispatch, useAppSelector } from '@/store';
import {
  fetchCategoryMessagesAction,
  fetchNotificationBootstrapAction,
  fetchNotificationMetaAction,
  markCategoryReadAction,
} from '@/store/modules/notification';
import { isAuthenticated } from '@/utils/storage';
import { useAuthModal } from '@/hooks/useAuthModal';
import { formatApiError } from '@/utils/apiError';
import { usePageRefresh } from '@/hooks/usePageRefresh';
import { useActiveRouteView } from '@/hooks/useActiveRouteView';
import type { NotificationCategoryKey } from '@/types/notification';
import { NOTIFICATION_CATEGORY_LIST } from '@/views/Notifications/constants';

const emptyMessages = {
  items: [],
  loading: false,
  loadingMore: false,
  hasMore: false,
  loaded: false,
  error: false,
};

export function useNotificationsPage() {
  const dispatch = useAppDispatch();
  const { message } = App.useApp();
  const location = useLocation();
  const navigate = useNavigate();
  const { openAuth } = useAuthModal();
  const isActiveRoute = useActiveRouteView();
  const {
    summary,
    categories,
    categoryMessages,
    categoryRefreshRequired,
    metaLoaded,
    summaryLoading,
  } = useAppSelector((state) => state.notification);
  const [expandedKey, setExpandedKey] =
    useState<NotificationCategoryKey | null>(null);
  const markingCategoryRef = useRef<NotificationCategoryKey | null>(null);
  const notificationEntryKeyRef = useRef<string | null>(null);

  const refreshNotifications = useCallback(
    async (refreshAllCategories = false) => {
      const categoriesToRefresh = NOTIFICATION_CATEGORY_LIST.map(
        (config) => config.key,
      ).filter((key) => {
        const bucket = categoryMessages[key];
        return Boolean(
          refreshAllCategories ||
          categoryRefreshRequired[key] ||
          (bucket?.loaded && !bucket.loading && !bucket.loadingMore),
        );
      });

      await Promise.all([
        dispatch(
          metaLoaded
            ? fetchNotificationMetaAction()
            : fetchNotificationBootstrapAction(),
        ).unwrap(),
        ...categoriesToRefresh.map((category) =>
          dispatch(fetchCategoryMessagesAction({ category })).unwrap(),
        ),
      ]);
    },
    [categoryMessages, categoryRefreshRequired, dispatch, metaLoaded],
  );

  useEffect(() => {
    if (!isActiveRoute) return;
    if (!isAuthenticated()) {
      openAuth('login');
      return;
    }
    // MainLayout 会缓存页面实例，不能只依赖首次挂载；每次真正进入消息页都刷新。
    if (notificationEntryKeyRef.current === location.key) return;
    notificationEntryKeyRef.current = location.key;
    void refreshNotifications(true).catch((err) => {
      message.error(formatApiError('刷新通知失败', err));
    });
  }, [isActiveRoute, location.key, message, openAuth, refreshNotifications]);

  const markCategoryRead = useCallback(
    async (key: NotificationCategoryKey) => {
      if (markingCategoryRef.current === key) return;

      markingCategoryRef.current = key;
      try {
        await dispatch(markCategoryReadAction(key)).unwrap();
      } finally {
        if (markingCategoryRef.current === key) {
          markingCategoryRef.current = null;
        }
      }
    },
    [dispatch],
  );

  /** 只有已展开的分类，才消费 SSE 的刷新标记并重新请求消息。 */
  useEffect(() => {
    if (!isActiveRoute || !expandedKey) return;
    const bucket = categoryMessages[expandedKey];
    if (
      !categoryRefreshRequired[expandedKey] ||
      !bucket?.loaded ||
      bucket.loading ||
      bucket.error
    ) {
      return;
    }

    void dispatch(fetchCategoryMessagesAction({ category: expandedKey }))
      .unwrap()
      .then(() => markCategoryRead(expandedKey))
      .catch(() => undefined);
  }, [
    categoryMessages,
    categoryRefreshRequired,
    dispatch,
    expandedKey,
    isActiveRoute,
    markCategoryRead,
  ]);

  const toggleCategory = useCallback(
    async (key: NotificationCategoryKey) => {
      if (!isAuthenticated()) {
        openAuth('login');
        return;
      }

      if (expandedKey === key) {
        setExpandedKey(null);
        return;
      }

      setExpandedKey(key);
      try {
        await dispatch(fetchCategoryMessagesAction({ category: key })).unwrap();
        await markCategoryRead(key);
      } catch (err) {
        message.error(formatApiError('加载通知失败', err));
      }
    },
    [dispatch, expandedKey, markCategoryRead, message, openAuth],
  );

  usePageRefresh(refreshNotifications, true);

  const loadMore = useCallback(
    (key: NotificationCategoryKey) => {
      void dispatch(
        fetchCategoryMessagesAction({ category: key, append: true }),
      );
    },
    [dispatch],
  );

  const handleNavigate = useCallback(
    (path: string) => {
      navigate(path);
    },
    [navigate],
  );

  const panels = NOTIFICATION_CATEGORY_LIST.map((config) => ({
    config,
    unreadCount:
      categories.find((c) => c.category === config.key)?.unreadCount ?? 0,
    expanded: expandedKey === config.key,
    messages: categoryMessages[config.key] ?? emptyMessages,
  }));

  return {
    summary,
    summaryLoading,
    panels,
    toggleCategory,
    loadMore,
    handleNavigate,
  };
}
