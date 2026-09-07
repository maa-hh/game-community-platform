import React, { memo } from 'react';
import type { FC, MouseEventHandler } from 'react';

import PostCoverThumb from '@/base-ui/PostCoverThumb';
import SteamCoverImage from '@/base-ui/SteamCoverImage';
import type { PostCoverSource } from '@/utils/postCover';
import { resolveGameCoverUrl } from '@/utils/steamImage';

interface NotificationPostCoverProps {
  coverSource?: PostCoverSource;
  coverUrl?: string;
  gameAppId?: number;
  gameCoverUrl?: string;
  title?: string;
  onClick?: MouseEventHandler<HTMLButtonElement>;
}

const NotificationPostCover: FC<NotificationPostCoverProps> = ({
  coverSource,
  coverUrl,
  gameAppId,
  gameCoverUrl,
  title = '帖子',
  onClick,
}) => {
  const normalizedGameAppId = Number(gameAppId);
  const hasGameAppId =
    Number.isInteger(normalizedGameAppId) && normalizedGameAppId > 0;
  const fallbackSource: PostCoverSource = {
    title,
    coverUrl: coverUrl?.trim() || undefined,
    images: coverUrl?.trim() ? [coverUrl.trim()] : undefined,
  };
  const gameSource: PostCoverSource | undefined = hasGameAppId
    ? {
        title,
        coverUrl: resolveGameCoverUrl(normalizedGameAppId, gameCoverUrl),
      }
    : undefined;
  const source = coverSource ?? gameSource ?? fallbackSource;
  const isGameCover = hasGameAppId && !coverSource;

  return (
    <button
      type="button"
      className="notification-post-cover"
      onClick={onClick}
      aria-label={isGameCover ? '查看游戏评分' : '查看帖子'}
    >
      {isGameCover ? (
        <SteamCoverImage
          appId={normalizedGameAppId}
          name={title}
          coverUrl={gameCoverUrl}
          className="notification-post-cover__game-image"
          fallback={<PostCoverThumb source={source} />}
        />
      ) : (
        <PostCoverThumb source={source} />
      )}
    </button>
  );
};

export default memo(NotificationPostCover);
