import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { message } from 'antd';

import { useAppDispatch, useAppSelector } from '@/store';
import {
  fetchCategoryMessagesAction,
  fetchNotificationBootstrapAction,
  markCategoryReadAction,
  resetAllCategoryMessages,
  setActiveNotificationCategory,
} from '@/store/modules/notification';
import { isAuthenticated } from '@/utils/storage';
import { useAuthModal } from '@/hooks/useAuthModal';
import { formatApiError } from '@/utils/apiError';
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
  const navigate = useNavigate();
  const { openAuth } = useAuthModal();
  const { summary, categories, categoryMessages } = useAppSelector(
    (state) => state.notification,
  );
  const [expandedKey, setExpandedKey] =
    useState<NotificationCategoryKey | null>(null);
  const markingCategoryRef = useRef<NotificationCategoryKey | null>(null);

  useEffect(() => {
    if (!isAuthenticated()) {
      openAuth('login');
      return;
    }
    dispatch(resetAllCategoryMessages());
    void dispatch(fetchNotificationBootstrapAction());
  }, [dispatch, openAuth]);

  useEffect(() => {
    dispatch(setActiveNotificationCategory(expandedKey));
    return () => {
      dispatch(setActiveNotificationCategory(null));
    };
  }, [dispatch, expandedKey]);

  /** 分类保持展开时自动已读（含 SSE 推送新通知、列表 refetch 后） */
  useEffect(() => {
    if (!expandedKey) return;
    const bucket = categoryMessages[expandedKey];
    if (!bucket?.loaded || bucket.loading) return;

    const categoryUnread =
      categories.find((c) => c.category === expandedKey)?.unreadCount ?? 0;
    const hasUnreadItems = bucket.items.some(
      (item) => (item.readStatus ?? 0) === 0,
    );
    if (categoryUnread <= 0 && !hasUnreadItems) return;
    if (markingCategoryRef.current === expandedKey) return;

    markingCategoryRef.current = expandedKey;
    void dispatch(markCategoryReadAction(expandedKey)).finally(() => {
      if (markingCategoryRef.current === expandedKey) {
        markingCategoryRef.current = null;
      }
    });
  }, [expandedKey, categoryMessages, categories, dispatch]);

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
      } catch (err) {
        message.error(formatApiError('加载通知失败', err));
      }
    },
    [dispatch, expandedKey, openAuth],
  );

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
    panels,
    toggleCategory,
    loadMore,
    handleNavigate,
  };
}
