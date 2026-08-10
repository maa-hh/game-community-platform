import { useCallback } from 'react';
import { message } from 'antd';

import { useRequireLogin } from '@/hooks/useRequireLogin';
import { togglePostLikeApi } from '@/service/social';
import { formatApiError } from '@/utils/apiError';

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
  const { requireLogin } = useRequireLogin();

  return useCallback(async () => {
    if (!articleId || !requireLogin()) return;

    const snapshot = readState();
    const nextLiked = !snapshot.liked;
    applyState({
      liked: nextLiked,
      likeCount: Math.max(0, snapshot.likeCount + (nextLiked ? 1 : -1)),
    });

    try {
      const res = await togglePostLikeApi(articleId, nextLiked);
      if (
        res.data.liked !== nextLiked ||
        res.data.likeCount !==
          Math.max(0, snapshot.likeCount + (nextLiked ? 1 : -1))
      ) {
        applyState({
          liked: res.data.liked,
          likeCount: res.data.likeCount,
        });
      }
    } catch (err) {
      applyState(snapshot);
      message.error(formatApiError('点赞失败', err));
    }
  }, [applyState, articleId, readState, requireLogin]);
}
