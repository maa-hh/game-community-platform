import { useCallback } from 'react';
import { App } from 'antd';

import { useOptimisticAction } from '@/hooks/useOptimisticAction';
import { useRequireLogin } from '@/hooks/useRequireLogin';
import { usePostInteractionActions } from '@/hooks/usePostInteraction';
import { togglePostLikeApi } from '@/service/social';
import { formatApiError } from '@/utils/apiError';
import { PROFILE_DATA_DOMAIN } from '@/types/profileRealtime';

export interface PostLikeState {
  liked: boolean;
  likeCount: number;
}

/** 帖子点赞：乐观更新 → 请求确认 → 失败回滚 */
export function usePostLikeAction(
  articleId: string | undefined,
  applyState: (next: PostLikeState) => void,
  readState: () => PostLikeState,
) {
  const { message } = App.useApp();
  const { requireLogin } = useRequireLogin();
  const {
    updateInteraction,
    invalidateCommunityFeed,
    invalidateProfileInteractionCaches,
  } = usePostInteractionActions();
  const { run: runOptimisticAction, isPending } = useOptimisticAction();

  return useCallback(async () => {
    if (!articleId || !requireLogin()) return;
    if (isPending('post-like')) return;

    const snapshot = readState();
    const nextLiked = !snapshot.liked;
    const nextLikeCount = Math.max(
      0,
      snapshot.likeCount + (nextLiked ? 1 : -1),
    );
    await runOptimisticAction('post-like', {
      apply: () => {
        applyState({
          liked: nextLiked,
          likeCount: nextLikeCount,
        });
        updateInteraction(articleId, {
          liked: nextLiked,
          likeCount: nextLikeCount,
          likePending: true,
        });
      },
      request: () => togglePostLikeApi(articleId, nextLiked),
      commit: (res) => {
        if (
          res.data.liked !== nextLiked ||
          res.data.likeCount !== nextLikeCount
        ) {
          applyState({
            liked: res.data.liked,
            likeCount: res.data.likeCount,
          });
        }
        updateInteraction(articleId, {
          liked: res.data.liked,
          likeCount: res.data.likeCount,
          likePending: false,
        });
        invalidateCommunityFeed();
        invalidateProfileInteractionCaches([
          PROFILE_DATA_DOMAIN.LIKED,
          PROFILE_DATA_DOMAIN.RECEIVED,
        ]);
      },
      rollback: (err) => {
        applyState({
          liked: snapshot.liked,
          likeCount: snapshot.likeCount,
        });
        updateInteraction(articleId, {
          liked: snapshot.liked,
          likeCount: snapshot.likeCount,
          likePending: false,
        });
        message.error(formatApiError('点赞失败', err));
      },
    });
  }, [
    applyState,
    articleId,
    invalidateCommunityFeed,
    invalidateProfileInteractionCaches,
    isPending,
    message,
    readState,
    requireLogin,
    runOptimisticAction,
    updateInteraction,
  ]);
}
