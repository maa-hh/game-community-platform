import { useCallback, useMemo } from 'react';
import type { Dispatch, SetStateAction } from 'react';

import { useFeedItemFavorite } from '@/hooks/useFeedItemFavorite';
import { useFeedItemLike } from '@/hooks/useFeedItemLike';
import { useInfiniteScroll } from '@/hooks/useInfiniteScroll';
import { useAppDispatch, useAppSelector } from '@/store';
import {
  serverApi,
  useCommunityFeedInfiniteQuery,
} from '@/store/services/serverApi';
import {
  applyFeedItemsUpdate,
  flattenFeedPages,
} from '@/store/services/feedCache';
import type { LatestPostItem } from '@/types/post';

export function useCommunityFeed() {
  const dispatch = useAppDispatch();
  const accountId = useAppSelector((state) => state.auth.user?.accountId);
  const queryArg = useMemo(
    () => ({ accountId: accountId ?? null }),
    [accountId],
  );
  const feed = useCommunityFeedInfiniteQuery(queryArg);
  const items = useMemo(
    () => flattenFeedPages(feed.data?.pages ?? []),
    [feed.data?.pages],
  );

  const setItems = useCallback<Dispatch<SetStateAction<LatestPostItem[]>>>(
    (update) => {
      dispatch(
        serverApi.util.updateQueryData('communityFeed', queryArg, (draft) => {
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

  const handleLike = useFeedItemLike(setItems);
  const handleFavorite = useFeedItemFavorite(setItems);

  return {
    items,
    loading: feed.isLoading,
    loadingMore: feed.isFetchingNextPage,
    refreshing: feed.isFetching && !feed.isLoading && !feed.isFetchingNextPage,
    hasMore: Boolean(feed.hasNextPage),
    sentinelRef,
    reload: feed.refetch,
    handleLike,
    handleFavorite,
  };
}
