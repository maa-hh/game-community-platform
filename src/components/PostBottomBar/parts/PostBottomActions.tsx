import React, { memo } from 'react';
import type { FC } from 'react';

import StatAction from '@/base-ui/StatAction';

interface PostBottomActionsProps {
  likeCount: number;
  favoriteCount: number;
  shareCount: number;
  liked: boolean;
  favorited: boolean;
  onLike: () => void;
  onFavorite: () => void;
  onShare: () => void;
}

const PostBottomActions: FC<PostBottomActionsProps> = ({
  likeCount,
  favoriteCount,
  shareCount,
  liked,
  favorited,
  onLike,
  onFavorite,
  onShare,
}) => (
  <div className="post-bottom-bar__actions">
    <StatAction kind="like" count={likeCount} active={liked} onClick={onLike} />
    <StatAction
      kind="favorite"
      count={favoriteCount}
      active={favorited}
      onClick={onFavorite}
    />
    <StatAction kind="share" count={shareCount} onClick={onShare} />
  </div>
);

export default memo(PostBottomActions);
