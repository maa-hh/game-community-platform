import { useCallback, useRef } from 'react';
import type { Dispatch, MutableRefObject, SetStateAction } from 'react';
import { App } from 'antd';

import { useRequireLogin } from '@/hooks/useRequireLogin';
import { useOptimisticAction } from '@/hooks/useOptimisticAction';
import { usePostInteractionActions } from '@/hooks/usePostInteraction';
import { togglePostLikeApi } from '@/service/social';
import { formatApiError } from '@/utils/apiError';
import { PROFILE_DATA_DOMAIN } from '@/types/profileRealtime';

/** 与后端 RecommendConstants.LIKE_WEIGHT 一致 */
export const LIKE_HOT_SCORE_DELTA = 2;

type LikableFeedItem = {
  id: string;
  liked?: boolean;
  likeCount?: number;
  hotScore?: number;
};

type LikeSnapshot = {
  liked: boolean;
  likeCount: number;
  hotScore?: number;
};

export interface UseFeedItemLikeOptions {
  /** 热榜场景：乐观更新同步调整热度分 */
  syncHotScore?: boolean;
  hotScoreDelta?: number;
  /** 进行中的点赞请求数（用于 SSE 等刷新避让） */
  inflightRef?: MutableRefObject<number>;
  /** 单次点赞结束（成功或失败）后回调 */
  onSettled?: () => void;
}

function buildOptimisticPatch<T extends LikableFeedItem>(
  row: T,
  nextLiked: boolean,
  options?: UseFeedItemLikeOptions,
): T {
  const likeCount = Math.max(0, (row.likeCount ?? 0) + (nextLiked ? 1 : -1));
  const next: T = {
    ...row,
    liked: nextLiked,
    likeCount,
  };
  if (options?.syncHotScore && row.hotScore != null) {
    const delta = options.hotScoreDelta ?? LIKE_HOT_SCORE_DELTA;
    next.hotScore = Math.max(0, row.hotScore + (nextLiked ? delta : -delta));
  }
  return next;
}

function takeSnapshot<T extends LikableFeedItem>(row: T): LikeSnapshot {
  return {
    liked: Boolean(row.liked),
    likeCount: row.likeCount ?? 0,
    hotScore: row.hotScore,
  };
}

function restoreSnapshot<T extends LikableFeedItem>(
  row: T,
  snapshot: LikeSnapshot,
): T {
  return {
    ...row,
    liked: snapshot.liked,
    likeCount: snapshot.likeCount,
    hotScore: snapshot.hotScore,
  };
}

function shouldSyncFromServer(
  row: LikableFeedItem,
  server: { liked: boolean; likeCount: number },
): boolean {
  return (
    Boolean(row.liked) !== server.liked ||
    (row.likeCount ?? 0) !== server.likeCount
  );
}

export function useFeedItemLike<T extends LikableFeedItem>(
  setItems: Dispatch<SetStateAction<T[]>>,
  options?: UseFeedItemLikeOptions,
) {
  const { message } = App.useApp();
  const { requireLogin } = useRequireLogin();
  const { updateInteraction, invalidateProfileInteractionCaches } =
    usePostInteractionActions();
  const optionsRef = useRef(options);
  const { run: runOptimisticAction } = useOptimisticAction();
  optionsRef.current = options;

  return useCallback(
    async (item: T) => {
      if (!requireLogin()) return;
      const opts = optionsRef.current;
      const snapshot = takeSnapshot(item);
      const nextLiked = !snapshot.liked;
      const nextLikeCount = Math.max(
        0,
        snapshot.likeCount + (nextLiked ? 1 : -1),
      );

      await runOptimisticAction(`feed-like:${item.id}`, {
        apply: () => {
          setItems((prev) => {
            const row = prev.find((entry) => entry.id === item.id);
            if (!row) return prev;
            return prev.map((entry) =>
              entry.id === item.id
                ? buildOptimisticPatch(entry, nextLiked, opts)
                : entry,
            );
          });
          updateInteraction(item.id, {
            liked: nextLiked,
            likeCount: nextLikeCount,
            likePending: true,
          });
          if (opts?.inflightRef) opts.inflightRef.current += 1;
        },
        request: () => togglePostLikeApi(item.id, nextLiked),
        commit: (res) => {
          setItems((prev) =>
            prev.map((row) => {
              if (row.id !== item.id) return row;
              if (!shouldSyncFromServer(row, res.data)) return row;
              return {
                ...row,
                liked: res.data.liked,
                likeCount: res.data.likeCount,
              };
            }),
          );
          updateInteraction(item.id, {
            liked: res.data.liked,
            likeCount: res.data.likeCount,
            likePending: false,
          });
          invalidateProfileInteractionCaches([
            PROFILE_DATA_DOMAIN.LIKED,
            PROFILE_DATA_DOMAIN.RECEIVED,
          ]);
        },
        rollback: (err) => {
          setItems((prev) =>
            prev.map((row) =>
              row.id === item.id ? restoreSnapshot(row, snapshot) : row,
            ),
          );
          updateInteraction(item.id, {
            liked: snapshot.liked,
            likeCount: snapshot.likeCount,
            likePending: false,
          });
          message.error(formatApiError('点赞失败', err));
        },
        finally: () => {
          if (opts?.inflightRef) {
            opts.inflightRef.current = Math.max(
              0,
              opts.inflightRef.current - 1,
            );
          }
          opts?.onSettled?.();
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
