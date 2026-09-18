import { useCallback, useEffect, useMemo, useRef } from 'react';
import type { Dispatch, SetStateAction } from 'react';
import { useLocation } from 'react-router-dom';

import { useFeedItemFavorite } from '@/hooks/useFeedItemFavorite';
import { useFeedItemLike } from '@/hooks/useFeedItemLike';
import { mapContentPostTypeToNumeric } from '@/utils/postType';
import store, { useAppDispatch, useAppSelector } from '@/store';
import { useInfiniteScroll } from '@/hooks/useInfiniteScroll';
import {
  serverApi,
  useFollowFeedInfiniteQuery,
} from '@/store/services/serverApi';
import {
  applyFeedItemsUpdate,
  flattenFeedPages,
} from '@/store/services/feedCache';
import type { LatestPostItem } from '@/types/post';
import {
  fetchNotificationMetaAction,
  markFeedReadAction,
} from '@/store/modules/notification';
import { clearFollowFeedReloadRequired } from '@/store/modules/profileRealtime';

import type { FeedTypeFilter } from './config';

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
  const queryArg = useMemo(
    () => ({
      accountId: accountId ?? null,
      postType:
        activeType === 'all'
          ? undefined
          : mapContentPostTypeToNumeric(activeType),
    }),
    [accountId, activeType],
  );
  const feed = useFollowFeedInfiniteQuery(queryArg);
  const items = useMemo(
    () => flattenFeedPages(feed.data?.pages ?? []),
    [feed.data?.pages],
  );
  const setItems = useCallback<Dispatch<SetStateAction<LatestPostItem[]>>>(
    (update) => {
      dispatch(
        serverApi.util.updateQueryData('followFeed', queryArg, (draft) => {
          applyFeedItemsUpdate(draft.pages, update);
        }),
      );
    },
    [dispatch, queryArg],
  );
  const loadMore = useCallback(async () => {
    if (!feed.hasNextPage || feed.isFetchingNextPage) return;
    await feed.fetchNextPage();
  }, [feed]);
  const { sentinelRef } = useInfiniteScroll({
    enabled: items.length > 0,
    hasMore: Boolean(feed.hasNextPage),
    loading: feed.isLoading,
    loadingMore: feed.isFetchingNextPage,
    onLoadMore: loadMore,
  });
  const reloadFeed = useCallback(async () => {
    await feed.refetch();
  }, [feed]);

  const refreshAndMarkFeedRead = useCallback(async () => {
    await reloadFeed();
    await dispatch(markFeedReadAction()).unwrap();
    // SSE 在刷新期间到达时，markFeedReadAction 可能因版本保护未覆盖新红点；
    // 只有确认当前 Redux 状态已清零，才消费重载标记。
    if (!store.getState().notification.summary.feedUnread) {
      dispatch(clearFollowFeedReloadRequired());
    }
  }, [dispatch, reloadFeed]);

  const handleLike = useFeedItemLike(setItems);
  const handleFavorite = useFeedItemFavorite(setItems);

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
    () => refreshAndMarkFeedRead(),
    [refreshAndMarkFeedRead],
  );

  return {
    items,
    loading: feed.isLoading,
    loadingMore: feed.isFetchingNextPage,
    refreshing: feed.isFetching && !feed.isLoading && !feed.isFetchingNextPage,
    hasMore: Boolean(feed.hasNextPage),
    sentinelRef,
    loadFeed: handleRefresh,
    handleLike,
    handleFavorite,
  };
}
