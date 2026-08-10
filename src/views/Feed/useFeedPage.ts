import { useCallback } from 'react';

import { useCursorList } from '@/hooks/useCursorList';
import { useFeedItemFavorite } from '@/hooks/useFeedItemFavorite';
import { useFeedItemLike } from '@/hooks/useFeedItemLike';
import { fetchFollowFeedApi } from '@/service/social';
import { mapContentPostTypeToNumeric } from '@/utils/postType';
import { useAppSelector } from '@/store';

import type { FeedTypeFilter } from './config';

const PAGE_SIZE = 20;

export function useFeedPage(activeType: FeedTypeFilter) {
  const accountId = useAppSelector((state) => state.auth.user?.accountId);
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

  const handleLike = useFeedItemLike(list.setItems);
  const handleFavorite = useFeedItemFavorite(list.setItems);

  return {
    items: list.items,
    loading: list.loading,
    loadingMore: list.loadingMore,
    hasMore: list.hasMore,
    sentinelRef: list.sentinelRef,
    loadFeed: list.reload,
    handleLike,
    handleFavorite,
  };
}
