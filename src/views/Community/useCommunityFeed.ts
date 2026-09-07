import { useCallback } from 'react';

import { useCursorList } from '@/hooks/useCursorList';
import { useFeedItemFavorite } from '@/hooks/useFeedItemFavorite';
import { useFeedItemLike } from '@/hooks/useFeedItemLike';
import { communityFeedCacheKey } from '@/hooks/usePostInteraction';
import { fetchLatestPostsPageApi } from '@/service/social';
import { useAppSelector } from '@/store';

const PAGE_SIZE = 20;

export function useCommunityFeed() {
  const accountId = useAppSelector((state) => state.auth.user?.accountId);
  const fetchBatch = useCallback(
    async (cursor: string | undefined, size: number) => {
      const res = await fetchLatestPostsPageApi({
        lastId: cursor,
        size,
      });
      return res.data;
    },
    [],
  );

  const list = useCursorList({
    pageSize: PAGE_SIZE,
    cacheKey: communityFeedCacheKey(accountId),
    getCursor: (item) => item.id,
    fetchBatch,
  });

  const handleLike = useFeedItemLike(list.setItems);
  const handleFavorite = useFeedItemFavorite(list.setItems);

  return {
    items: list.items,
    loading: list.loading,
    loadingMore: list.loadingMore,
    refreshing: list.refreshing,
    hasMore: list.hasMore,
    sentinelRef: list.sentinelRef,
    reload: list.reload,
    handleLike,
    handleFavorite,
  };
}
