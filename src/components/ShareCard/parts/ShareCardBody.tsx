import React, { memo } from 'react';
import type { FC } from 'react';

import ProfileUserLink from '@/components/ProfileUserLink';
import StatAction from '@/base-ui/StatAction';

import type { PostRefCard } from '@/types/post';

interface ShareCardBodyProps {
  data: PostRefCard;
  viewCount: number;
  commentCount: number;
  likeCount: number;
  liked: boolean;
  unavailable: boolean;
}

const ShareCardBody: FC<ShareCardBodyProps> = ({
  data,
  viewCount,
  commentCount,
  likeCount,
  liked,
  unavailable,
}) => (
  <div className="share-card__body">
    {!unavailable ? (
      <div className="share-card__author">
        <ProfileUserLink
          accountId={data.author.accountId}
          nickname={data.author.nickname}
          avatar={data.author.avatar}
          size={20}
          className="share-card__author-link"
        />
      </div>
    ) : null}
    <h4 className="share-card__title">
      {unavailable ? data.unavailableMessage || data.title : data.title}
    </h4>
    <div className="share-card__footer">
      {data.summary ? (
        <p className="share-card__summary">{data.summary}</p>
      ) : null}
      {!unavailable ? (
        <div
          className="share-card__stats"
          onClick={(e) => e.stopPropagation()}
          onKeyDown={(e) => e.stopPropagation()}
          role="presentation"
        >
          <StatAction kind="view" count={viewCount} size="sm" />
          <StatAction kind="comment" count={commentCount} size="sm" />
          <StatAction kind="like" count={likeCount} active={liked} size="sm" />
        </div>
      ) : null}
    </div>
  </div>
);

export default memo(ShareCardBody);
