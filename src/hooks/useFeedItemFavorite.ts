import { useCallback } from 'react';
import type { Dispatch, SetStateAction } from 'react';
import { message } from 'antd';

import { useRequireLogin } from '@/hooks/useRequireLogin';
import { toggleFavoriteApi } from '@/service/social';
import { formatApiError } from '@/utils/apiError';

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
  const { requireLogin } = useRequireLogin();

  return useCallback(
    async (item: T) => {
      if (!requireLogin()) return;

      const snapshot = takeSnapshot(item);
      const nextFavorited = !snapshot.favorited;

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

      try {
        const res = await toggleFavoriteApi(item.id, nextFavorited);
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
      } catch (err) {
        setItems((prev) =>
          prev.map((row) =>
            row.id === item.id ? restoreSnapshot(row, snapshot) : row,
          ),
        );
        message.error(formatApiError('收藏失败', err));
      }
    },
    [requireLogin, setItems],
  );
}
