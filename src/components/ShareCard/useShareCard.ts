import { useCallback } from 'react';
import type { KeyboardEvent, MouseEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { App } from 'antd';

import type { IShareCardProps } from './types';
import { resolveGameRefAppId } from '@/utils/gameRepost';
import {
  buildGameDetailNavigationState,
  buildPostDetailNavigationState,
  mapPostRefToLatestPost,
} from '@/utils/detailNavigation';
import { preloadGameDetail } from '@/router/preload';

export function useShareCard({
  data,
  preview = false,
  onClick,
}: IShareCardProps) {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const location = useLocation();
  const isVideo = !data.unavailable && data.postType === 'video';
  const cover = data.unavailable ? undefined : data.coverUrl;
  const posterTitle =
    !data.unavailable && !isVideo && !cover
      ? data.title?.trim() || data.summary?.trim()
      : undefined;
  const unavailable = Boolean(data.unavailable);

  const handleClick = useCallback(
    (e: MouseEvent<HTMLElement>) => {
      if (preview) return;
      e.stopPropagation();

      if (unavailable) {
        message.warning(
          data.unavailableMessage || '原帖已下架或删除，无法查看原帖',
        );
        return;
      }

      if (onClick) {
        onClick(e);
        return;
      }
      const gameAppId = resolveGameRefAppId(data.id);
      if (gameAppId) {
        void preloadGameDetail();
        navigate(`/game/${gameAppId}`, {
          state: buildGameDetailNavigationState(location, {
            appId: gameAppId,
            name: data.title || `游戏 ${gameAppId}`,
            coverUrl: data.coverUrl,
          }),
        });
        return;
      }
      const postPreview = mapPostRefToLatestPost(data);
      navigate(`/post/${data.id}`, {
        state: postPreview
          ? buildPostDetailNavigationState(location, postPreview)
          : undefined,
      });
    },
    [data, location, message, navigate, onClick, preview, unavailable],
  );

  const handleKeyDown = useCallback(
    (e: KeyboardEvent<HTMLElement>) => {
      if (preview) return;
      if (e.key === 'Enter') {
        handleClick(e as unknown as MouseEvent<HTMLElement>);
      }
    },
    [handleClick, preview],
  );

  return {
    isVideo,
    cover,
    posterTitle,
    unavailable,
    viewCount: data.viewCount ?? 0,
    commentCount: data.commentCount ?? 0,
    likeCount: data.likeCount ?? 0,
    liked: Boolean(data.liked),
    handleClick,
    handleKeyDown,
  };
}
