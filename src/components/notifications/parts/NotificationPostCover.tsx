import React, { memo } from 'react';
import type { FC, MouseEventHandler } from 'react';

import PostCoverThumb from '@/base-ui/PostCoverThumb';
import type { PostCoverSource } from '@/utils/postCover';

interface NotificationPostCoverProps {
  coverSource?: PostCoverSource;
  coverUrl?: string;
  title?: string;
  onClick?: MouseEventHandler<HTMLButtonElement>;
}

const NotificationPostCover: FC<NotificationPostCoverProps> = ({
  coverSource,
  coverUrl,
  title = '帖子',
  onClick,
}) => {
  const fallbackSource: PostCoverSource = {
    title,
    coverUrl: coverUrl?.trim() || undefined,
    images: coverUrl?.trim() ? [coverUrl.trim()] : undefined,
  };
  const source = coverSource ?? fallbackSource;

  return (
    <button
      type="button"
      className="notification-post-cover"
      onClick={onClick}
      aria-label="查看帖子"
    >
      <PostCoverThumb source={source} />
    </button>
  );
};

export default memo(NotificationPostCover);
