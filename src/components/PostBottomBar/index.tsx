import React, { memo } from 'react';
import type { FC } from 'react';

import PostBottomActions from './parts/PostBottomActions';
import PostBottomComposer from './parts/PostBottomComposer';
import type { IPostBottomBarProps } from './types';
import { usePostBottomBar } from './usePostBottomBar';

import './style.less';

/** 底部横幅：左评论框，右赞/藏/分享 */
const PostBottomBar: FC<IPostBottomBarProps> = ({
  likeCount,
  favoriteCount,
  shareCount,
  liked,
  favorited,
  likeDisabled,
  favoriteDisabled,
  submitting,
  className,
  onLike,
  onFavorite,
  onShare,
  onComment,
}) => {
  const { draft, setDraft, send } = usePostBottomBar(onComment);

  return (
    <div className={`post-bottom-bar${className ? ` ${className}` : ''}`}>
      <PostBottomComposer
        draft={draft}
        submitting={submitting}
        onDraftChange={setDraft}
        onSend={send}
      />
      <PostBottomActions
        likeCount={likeCount}
        favoriteCount={favoriteCount}
        shareCount={shareCount}
        liked={liked}
        favorited={favorited}
        likeDisabled={likeDisabled}
        favoriteDisabled={favoriteDisabled}
        onLike={onLike}
        onFavorite={onFavorite}
        onShare={onShare}
      />
    </div>
  );
};

export default memo(PostBottomBar);
