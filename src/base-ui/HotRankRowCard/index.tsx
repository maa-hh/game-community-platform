import React, { memo } from 'react';
import type { FC } from 'react';
import { FireFilled } from '@ant-design/icons';

import PostRowPreview from '@/base-ui/PostRowPreview';
import StatAction from '@/base-ui/StatAction';
import UserAvatarWithFrame from '@/base-ui/UserAvatarWithFrame';
import { formatCount } from '@/utils/formatCount';

import type { HotRankRowCardProps } from './types';
import { mapLatestPostToRowPreview } from './types';

import './style.less';

const HotRankRowCard: FC<HotRankRowCardProps> = ({
  item,
  rank,
  hotScore,
  className,
  onClick,
  onLikeClick,
}) => {
  const resolvedRank = rank ?? item.rank;
  const resolvedHotScore = hotScore ?? item.hotScore;
  const showHeat =
    resolvedHotScore != null &&
    Number.isFinite(resolvedHotScore) &&
    resolvedHotScore > 0;

  const rootClass = ['hot-rank-row-card', className].filter(Boolean).join(' ');

  return (
    <article className={rootClass} onClick={onClick} role="presentation">
      <PostRowPreview
        data={mapLatestPostToRowPreview(item)}
        rank={resolvedRank}
        showTime={false}
        metaExtra={
          <>
            <div className="hot-rank-row-card__author">
              <UserAvatarWithFrame
                accountId={item.author.accountId}
                name={item.author.nickname}
                src={item.author.avatar}
                size={18}
              />
              <span className="hot-rank-row-card__nickname">
                {item.author.nickname}
              </span>
            </div>

            <div className="hot-rank-row-card__stats">
              <StatAction
                kind="view"
                count={item.viewCount}
                size="sm"
                className="hot-rank-row-card__stat"
              />
              <StatAction
                kind="like"
                count={item.likeCount}
                active={item.liked}
                size="sm"
                className="hot-rank-row-card__stat"
                stopPropagation
                onClick={onLikeClick}
              />
              <StatAction
                kind="comment"
                count={item.commentCount}
                size="sm"
                className="hot-rank-row-card__stat"
              />
              {showHeat ? (
                <span
                  className="hot-rank-row-card__heat"
                  aria-label={`热度 ${Math.round(resolvedHotScore!)}`}
                >
                  <FireFilled className="hot-rank-row-card__heat-icon" />
                  <span className="hot-rank-row-card__heat-value">
                    {formatCount(Math.round(resolvedHotScore!))}
                  </span>
                </span>
              ) : null}
            </div>
          </>
        }
      />
    </article>
  );
};

export default memo(HotRankRowCard);

export type { HotRankRowCardProps } from './types';
export { mapLatestPostToRowPreview } from './types';
