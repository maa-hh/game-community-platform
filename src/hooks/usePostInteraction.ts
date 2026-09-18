import { useCallback } from 'react';

import { useAppDispatch, useAppSelector } from '@/store';
import {
  getPostInteractionScope,
  updatePostInteraction,
} from '@/store/modules/postInteraction';
import { invalidatePageDataCache } from '@/hooks/pageDataCache';
import { invalidateProfileDataCaches } from '@/utils/profileDataCache';
import type { ProfileDataDomain } from '@/types/profileRealtime';
import type { PostInteraction } from '@/store/modules/postInteraction';
import { serverApi } from '@/store/services/serverApi';

export { getPostInteractionScope } from '@/store/modules/postInteraction';

const EMPTY_INTERACTION: PostInteraction = {};

export function communityFeedCacheKey(accountId?: number | null): string {
  return `community:${getPostInteractionScope(accountId)}:latest`;
}

export function usePostInteraction(postId?: string) {
  const accountId = useAppSelector((state) => state.auth.user?.accountId);
  const scope = getPostInteractionScope(accountId);

  return useAppSelector(
    (state) =>
      (postId && state.postInteraction.byAccount[scope]?.[postId]) ||
      EMPTY_INTERACTION,
  );
}

export function usePostInteractionActions() {
  const dispatch = useAppDispatch();
  const accountId = useAppSelector((state) => state.auth.user?.accountId);

  const updateInteraction = useCallback(
    (postId: string, patch: PostInteraction) => {
      dispatch(updatePostInteraction({ accountId, postId, patch }));
    },
    [accountId, dispatch],
  );

  const invalidateCommunityFeed = useCallback(() => {
    invalidatePageDataCache(communityFeedCacheKey(accountId));
    dispatch(
      serverApi.util.invalidateTags([
        {
          type: 'CommunityFeed',
          id: String(accountId ?? 'anonymous'),
        },
      ]),
    );
  }, [accountId, dispatch]);

  const invalidateProfileInteractionCaches = useCallback(
    (domains: readonly ProfileDataDomain[]) => {
      invalidateProfileDataCaches(accountId, domains);
    },
    [accountId],
  );

  return {
    updateInteraction,
    invalidateCommunityFeed,
    invalidateProfileInteractionCaches,
  };
}

export function applyPostInteraction<T extends PostInteraction>(
  item: T,
  interaction?: PostInteraction,
): T {
  if (!interaction) return item;

  const next = { ...item };
  if (interaction.liked != null) next.liked = interaction.liked;
  if (interaction.favorited != null) next.favorited = interaction.favorited;
  if (interaction.likeCount != null) next.likeCount = interaction.likeCount;
  if (interaction.favoriteCount != null) {
    next.favoriteCount = interaction.favoriteCount;
  }
  if (interaction.likePending != null) {
    next.likePending = interaction.likePending;
  }
  if (interaction.favoritePending != null) {
    next.favoritePending = interaction.favoritePending;
  }
  return next;
}
