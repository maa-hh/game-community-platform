import React, { memo } from 'react';
import type { FC } from 'react';

import StatAction from '@/base-ui/StatAction';

interface PostBottomActionsProps {
  likeCount: number;
  favoriteCount: number;
  shareCount: number;
  liked: boolean;
  favorited: boolean;
  likeDisabled?: boolean;
  favoriteDisabled?: boolean;
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
  likeDisabled,
  favoriteDisabled,
  onLike,
  onFavorite,
  onShare,
}) => (
  <div className="post-bottom-bar__actions">
    <StatAction
      kind="like"
      count={likeCount}
      active={liked}
      disabled={likeDisabled}
      onClick={onLike}
    />
    <StatAction
      kind="favorite"
      count={favoriteCount}
      active={favorited}
      disabled={favoriteDisabled}
      onClick={onFavorite}
    />
    <StatAction kind="share" count={shareCount} onClick={onShare} />
  </div>
);

export default memo(PostBottomActions);
