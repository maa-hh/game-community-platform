import React, { memo, useMemo, useState } from 'react';
import type { FC, MouseEventHandler } from 'react';
import { useLocation } from 'react-router-dom';
import { RetweetOutlined, FireFilled } from '@ant-design/icons';

import FeedCardCover from '@/base-ui/FeedCardCover';
import OverflowTagRow from '@/base-ui/OverflowTagRow';
import type { OverflowTagRowItem } from '@/base-ui/OverflowTagRow';
import StatAction from '@/base-ui/StatAction';
import UserAvatarWithFrame from '@/base-ui/UserAvatarWithFrame';
import type { LatestPostItem } from '@/types/post';
import { resolvePostCardTitle, resolvePostCoverMedia } from '@/utils/postCover';
import { getFeedCardMediaCssVars } from '@/utils/feedCardCoverLayout';
import { resolvePostDisplayTags } from '@/utils/categoryTag';
import { resolvePostType } from '@/utils/postType';
import { formatCount } from '@/utils/formatCount';
import { buildGameDetailNavigationState } from '@/utils/detailNavigation';
import { preloadGameDetail } from '@/router/preload';

import './style.less';

export interface IFeedMasonryCardProps {
  item: LatestPostItem;
  /** 热榜场景：标题行右侧火苗排名 + 热度值 */
  rank?: number;
  hotScore?: number;
  onClick?: MouseEventHandler<HTMLElement>;
  onIntent?: () => void;
  onLikeClick?: MouseEventHandler<HTMLButtonElement>;
  onFavoriteClick?: MouseEventHandler<HTMLButtonElement>;
}

const FeedMasonryCard: FC<IFeedMasonryCardProps> = ({
  item,
  rank,
  hotScore,
  onClick,
  onIntent,
  onLikeClick,
  onFavoriteClick,
}) => {
  const location = useLocation();
  const [coverError, setCoverError] = useState(false);
  const postType = resolvePostType(item);
  const isRepost = postType === 'repost';
  const coverMedia = resolvePostCoverMedia(item);
  const { isVideo, coverUrl, videoUrl, posterTitle } = coverMedia;
  const title = resolvePostCardTitle(item);
  const summary = item.summary?.trim() || item.content?.trim() || '';
  const usePoster = !isVideo && (!coverUrl || coverError);
  const displayTags = useMemo(
    () => resolvePostDisplayTags(item.tags),
    [item.tags],
  );
  const tagItems = useMemo(() => {
    const tags: OverflowTagRowItem[] = [];

    if (isRepost) {
      tags.push({
        key: 'repost',
        text: '转发',
        variant: 'repost',
        leading: (
          <RetweetOutlined
            className="feed-masonry-card__repost-icon"
            aria-hidden
          />
        ),
      });
    }

    displayTags.forEach((tag) => {
      tags.push({
        key: `category-${tag.text}-${tag.icon || ''}`,
        text: tag.text,
        icon: tag.icon,
      });
    });

    item.gameTags?.forEach((tag) => {
      tags.push({
        key: `game-${tag.appId}`,
        text: tag.name,
        icon: tag.iconUrl,
        variant: 'game',
        to: `/game/${tag.appId}`,
        state: buildGameDetailNavigationState(location, {
          appId: tag.appId,
          name: tag.name,
          coverUrl: tag.iconUrl,
        }),
        onClick: (event) => event.stopPropagation(),
        onIntent: () => void preloadGameDetail(),
      });
    });

    return tags;
  }, [displayTags, isRepost, item.gameTags, location]);
  const mediaCssVars = useMemo(() => getFeedCardMediaCssVars(), []);
  const resolvedRank = rank ?? item.rank;
  const resolvedHotScore = hotScore ?? item.hotScore;
  const showHeatBadge =
    resolvedRank != null &&
    resolvedRank > 0 &&
    resolvedHotScore != null &&
    Number.isFinite(resolvedHotScore) &&
    resolvedHotScore > 0;

  const coverMode = isVideo ? 'video' : usePoster ? 'poster' : 'image';

  return (
    <article
      className="feed-masonry-card"
      style={mediaCssVars}
      onClick={onClick}
      onMouseEnter={onIntent}
      onPointerDown={onIntent}
      role={onClick ? 'presentation' : undefined}
    >
      <FeedCardCover
        mode={coverMode}
        title={posterTitle}
        coverUrl={coverUrl}
        videoUrl={videoUrl}
        onImageError={() => setCoverError(true)}
      />

      <div className="feed-masonry-card__body">
        <div className="feed-masonry-card__title-row">
          <h3 className="feed-masonry-card__title" title={title}>
            {title}
          </h3>

          {showHeatBadge ? (
            <div
              className="feed-masonry-card__heat"
              aria-label={`第 ${resolvedRank} 名，热度 ${Math.round(resolvedHotScore)}`}
            >
              <span className="feed-masonry-card__heat-flame">
                <FireFilled aria-hidden />
                <span className="feed-masonry-card__heat-rank">
                  {resolvedRank}
                </span>
              </span>
              <span className="feed-masonry-card__heat-value">
                {formatCount(Math.round(resolvedHotScore))}
              </span>
            </div>
          ) : null}
        </div>

        {summary ? (
          <p className="feed-masonry-card__summary" title={summary}>
            {summary}
          </p>
        ) : null}

        {tagItems.length > 0 ? (
          <OverflowTagRow
            className="feed-masonry-card__tags"
            preset="feed"
            items={tagItems}
          />
        ) : null}

        <footer className="feed-masonry-card__footer">
          <div className="feed-masonry-card__author">
            <UserAvatarWithFrame
              accountId={item.author.accountId}
              name={item.author.nickname}
              src={item.author.avatar}
              size={20}
            />
            <span className="feed-masonry-card__nickname">
              {item.author.nickname}
            </span>
          </div>

          <div className="feed-masonry-card__actions">
            <StatAction
              kind="view"
              count={item.viewCount}
              size="sm"
              className="feed-masonry-card__action"
            />
            <StatAction
              kind="like"
              count={item.likeCount}
              active={item.liked}
              disabled={item.likePending}
              size="sm"
              className="feed-masonry-card__action"
              stopPropagation
              onClick={onLikeClick}
            />
            <StatAction
              kind="favorite"
              count={item.favoriteCount}
              active={item.favorited}
              disabled={item.favoritePending}
              size="sm"
              className="feed-masonry-card__action"
              stopPropagation
              onClick={onFavoriteClick}
            />
          </div>
        </footer>
      </div>
    </article>
  );
};

export default memo(FeedMasonryCard);
