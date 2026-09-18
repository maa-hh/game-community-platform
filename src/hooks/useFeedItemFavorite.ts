import { useCallback } from 'react';
import type { Dispatch, SetStateAction } from 'react';
import { App } from 'antd';

import { useRequireLogin } from '@/hooks/useRequireLogin';
import { useOptimisticAction } from '@/hooks/useOptimisticAction';
import { usePostInteractionActions } from '@/hooks/usePostInteraction';
import { toggleFavoriteApi } from '@/service/social';
import { formatApiError } from '@/utils/apiError';
import { PROFILE_DATA_DOMAIN } from '@/types/profileRealtime';

type FavoritableFeedItem = {
  id: string;
  favorited?: boolean;
  favoriteCount?: number;
};

type FavoriteSnapshot = {
  favorited: boolean;
  favoriteCount: number;
};

function takeSnapshot<T extends FavoritableFeedItem>(row: T): FavoriteSnapshot {
  return {
    favorited: Boolean(row.favorited),
    favoriteCount: row.favoriteCount ?? 0,
  };
}

function restoreSnapshot<T extends FavoritableFeedItem>(
  row: T,
  snapshot: FavoriteSnapshot,
): T {
  return {
    ...row,
    favorited: snapshot.favorited,
    favoriteCount: snapshot.favoriteCount,
  };
}

export function useFeedItemFavorite<T extends FavoritableFeedItem>(
  setItems: Dispatch<SetStateAction<T[]>>,
) {
  const { message } = App.useApp();
  const { requireLogin } = useRequireLogin();
  const { updateInteraction, invalidateProfileInteractionCaches } =
    usePostInteractionActions();
  const { run: runOptimisticAction } = useOptimisticAction();

  return useCallback(
    async (item: T) => {
      if (!requireLogin()) return;
      const snapshot = takeSnapshot(item);
      const nextFavorited = !snapshot.favorited;
      const nextFavoriteCount = Math.max(
        0,
        snapshot.favoriteCount + (nextFavorited ? 1 : -1),
      );

      await runOptimisticAction(`feed-favorite:${item.id}`, {
        apply: () => {
          setItems((prev) => {
            const row = prev.find((entry) => entry.id === item.id);
            if (!row) return prev;
            const favoriteCount = Math.max(
              0,
              (row.favoriteCount ?? 0) + (nextFavorited ? 1 : -1),
            );
            return prev.map((entry) =>
              entry.id === item.id
                ? { ...entry, favorited: nextFavorited, favoriteCount }
                : entry,
            );
          });
          updateInteraction(item.id, {
            favorited: nextFavorited,
            favoriteCount: nextFavoriteCount,
            favoritePending: true,
          });
        },
        request: () => toggleFavoriteApi(item.id, nextFavorited),
        commit: (res) => {
          setItems((prev) =>
            prev.map((row) =>
              row.id === item.id
                ? {
                    ...row,
                    favorited: res.data.favorited,
                    favoriteCount: res.data.favoriteCount,
                  }
                : row,
            ),
          );
          updateInteraction(item.id, {
            favorited: res.data.favorited,
            favoriteCount: res.data.favoriteCount,
            favoritePending: false,
          });
          invalidateProfileInteractionCaches([PROFILE_DATA_DOMAIN.FAVORITES]);
        },
        rollback: (err) => {
          setItems((prev) =>
            prev.map((row) =>
              row.id === item.id ? restoreSnapshot(row, snapshot) : row,
            ),
          );
          updateInteraction(item.id, {
            favorited: snapshot.favorited,
            favoriteCount: snapshot.favoriteCount,
            favoritePending: false,
          });
          message.error(formatApiError('收藏失败', err));
        },
      });
    },
    [
      invalidateProfileInteractionCaches,
      message,
      requireLogin,
      runOptimisticAction,
      setItems,
      updateInteraction,
    ],
  );
}
