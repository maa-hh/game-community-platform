import { useCallback, useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';

import { useCursorList } from '@/hooks/useCursorList';
import { useFeedItemFavorite } from '@/hooks/useFeedItemFavorite';
import { useFeedItemLike } from '@/hooks/useFeedItemLike';
import { fetchFollowFeedApi } from '@/service/social';
import { mapContentPostTypeToNumeric } from '@/utils/postType';
import store, { useAppDispatch, useAppSelector } from '@/store';
import {
  fetchNotificationMetaAction,
  markFeedReadAction,
} from '@/store/modules/notification';
import { clearFollowFeedReloadRequired } from '@/store/modules/profileRealtime';

import type { FeedTypeFilter } from './config';

const PAGE_SIZE = 20;

export function useFeedPage(activeType: FeedTypeFilter) {
  const location = useLocation();
  const dispatch = useAppDispatch();
  const accountId = useAppSelector((state) => state.auth.user?.accountId);
  const followFeedReloadRequired = useAppSelector(
    (state) => state.profileRealtime.followFeedReloadRequired,
  );
  const feedUnreadCount = useAppSelector(
    (state) => state.notification.summary.feedUnreadCount,
  );
  const feedReadAccountRef = useRef<number | null>(null);
  const fetchBatch = useCallback(
    async (cursor: string | undefined, size: number) => {
      const postType =
        activeType === 'all'
          ? undefined
          : mapContentPostTypeToNumeric(activeType);
      const res = await fetchFollowFeedApi({
        before: cursor,
        size,
        postType,
      });
      return res.data;
    },
    [activeType],
  );

  const list = useCursorList({
    pageSize: PAGE_SIZE,
    cacheKey: `feed:${accountId ?? 'anonymous'}:${activeType}`,
    resetDeps: [activeType],
    getCursor: (item) => item.sortTime,
    fetchBatch,
  });
  const reloadFeed = list.reload;

  const refreshAndMarkFeedRead = useCallback(
    async (silent = true) => {
      await reloadFeed(silent);
      await dispatch(markFeedReadAction()).unwrap();
      // SSE 在刷新期间到达时，markFeedReadAction 可能因版本保护未覆盖新红点；
      // 只有确认当前 Redux 状态已清零，才消费重载标记。
      if (!store.getState().notification.summary.feedUnread) {
        dispatch(clearFollowFeedReloadRequired());
      }
    },
    [dispatch, reloadFeed],
  );

  const handleLike = useFeedItemLike(list.setItems);
  const handleFavorite = useFeedItemFavorite(list.setItems);

  useEffect(() => {
    if (!accountId || feedReadAccountRef.current === accountId) return;

    feedReadAccountRef.current = accountId;
    void (async () => {
      try {
        await dispatch(fetchNotificationMetaAction()).unwrap();
        await refreshAndMarkFeedRead();
      } catch {
        feedReadAccountRef.current = null;
      }
    })();
  }, [accountId, dispatch, refreshAndMarkFeedRead]);

  useEffect(() => {
    if (
      !accountId ||
      !followFeedReloadRequired ||
      location.pathname !== '/feed'
    )
      return;

    void refreshAndMarkFeedRead().catch(() => undefined);
  }, [
    accountId,
    feedUnreadCount,
    followFeedReloadRequired,
    location.pathname,
    refreshAndMarkFeedRead,
  ]);

  const handleRefresh = useCallback(
    () => refreshAndMarkFeedRead(false),
    [refreshAndMarkFeedRead],
  );

  return {
    items: list.items,
    loading: list.loading,
    loadingMore: list.loadingMore,
    refreshing: list.refreshing,
    hasMore: list.hasMore,
    sentinelRef: list.sentinelRef,
    loadFeed: handleRefresh,
    handleLike,
    handleFavorite,
  };
}
