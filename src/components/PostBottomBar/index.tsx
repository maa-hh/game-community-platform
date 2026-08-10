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
  submitting,
  className,
  onLike,
  onFavorite,
  onShare,
  onComment,
  onFocusComment,
}) => {
  const { draft, setDraft, send } = usePostBottomBar(onComment);

  return (
    <div className={`post-bottom-bar${className ? ` ${className}` : ''}`}>
      <PostBottomComposer
        draft={draft}
        submitting={submitting}
        onDraftChange={setDraft}
        onSend={send}
        onFocusComment={onFocusComment}
      />
      <PostBottomActions
        likeCount={likeCount}
        favoriteCount={favoriteCount}
        shareCount={shareCount}
        liked={liked}
        favorited={favorited}
        onLike={onLike}
        onFavorite={onFavorite}
        onShare={onShare}
      />
    </div>
  );
};

export default memo(PostBottomBar);
