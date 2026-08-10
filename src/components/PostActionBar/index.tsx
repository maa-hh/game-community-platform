import React, { memo } from 'react';
import type { FC } from 'react';

import StatAction from '@/base-ui/StatAction';

import type { IProps } from './types';

import './style.less';

/** 详情互动条：浏览 + 赞/藏/分享（差异仅数据） */
const PostActionBar: FC<IProps> = ({
  viewCount,
  likeCount,
  favoriteCount,
  shareCount,
  liked,
  favorited,
  onLike,
  onFavorite,
  onShare,
}) => {
  return (
    <div className="post-action-bar">
      <StatAction kind="view" count={viewCount} />
      <StatAction
        kind="like"
        count={likeCount}
        active={liked}
        onClick={onLike}
      />
      <StatAction
        kind="favorite"
        count={favoriteCount}
        active={favorited}
        onClick={onFavorite}
      />
      {onShare ? (
        <StatAction kind="share" count={shareCount} onClick={onShare} />
      ) : null}
    </div>
  );
};

export default memo(PostActionBar);
