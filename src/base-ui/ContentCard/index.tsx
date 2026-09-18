import React, { memo } from 'react';
import type { FC, MouseEventHandler, ReactNode } from 'react';
import { Link, useLocation } from 'react-router-dom';

import type { IUserDecoration } from '@/types/cosmetic';
import { postCardStyle } from '@/utils/cosmeticAsset';
import { resolveAvatarFrameAsset } from '@/constants/avatarFrameCatalog';
import ImageStrip from '@/base-ui/ContentCard/ImageStrip';
import TextCoverPoster from '@/base-ui/TextCoverPoster';
import type { ContentCardData } from '@/types/content';
import { resolvePostType } from '@/utils/postType';
import RepostBlock from '@/components/RepostBlock';
import VideoCover from '@/base-ui/VideoCover';
import StatAction from '@/base-ui/StatAction';
import ProfileUserLink from '@/components/ProfileUserLink';
import { formatCardTime } from '@/utils/formatTime';
import { buildGameDetailNavigationState } from '@/utils/detailNavigation';
import { preloadGameDetail } from '@/router/preload';

import './style.less';

interface IProps {
  data: ContentCardData;
  className?: string;
  authorDecoration?: IUserDecoration;
  /** 作者操作文字链，显示在头像行右侧 */
  authorExtra?: ReactNode;
  onLikeClick?: MouseEventHandler<HTMLButtonElement>;
  onCommentClick?: MouseEventHandler<HTMLButtonElement>;
  onClick?: MouseEventHandler<HTMLElement>;
  onIntent?: () => void;
}

const ContentCard: FC<IProps> = ({
  data,
  className,
  authorDecoration,
  authorExtra,
  onLikeClick,
  onCommentClick,
  onClick,
  onIntent,
}) => {
  const location = useLocation();
  const {
    author,
    title,
    summary,
    content,
    images = [],
    coverUrl,
    videoUrl,
    refPost,
    tags = [],
    gameTags = [],
    viewCount,
    commentCount,
    likeCount,
    liked = false,
    createdAt,
    rank,
    hotScore,
  } = data;

  const postType = resolvePostType(data);
  const isVideo = postType === 'video';
  const isRepost = postType === 'repost';

  const showHeat =
    hotScore != null && Number.isFinite(hotScore) && hotScore > 0;
  const showRankBadge = rank != null && rank > 0 && showHeat;

  const galleryImages =
    postType === 'image_text' || postType === 'article'
      ? images
      : postType === 'repost' && (coverUrl || images[0])
        ? [coverUrl || images[0]!]
        : [];
  const showVideoCover = isVideo && Boolean(coverUrl || videoUrl);
  const showGallery = !isVideo && !isRepost && galleryImages.length > 0;
  const showTitlePoster =
    !isVideo && !isRepost && !showGallery && Boolean(title?.trim());
  const summaryText = !isRepost ? summary?.trim() || content?.trim() || '' : '';
  const cardStyle = postCardStyle(authorDecoration?.postCard?.assetJson);
  const frameUrl = resolveAvatarFrameAsset(
    authorDecoration?.avatarFrame?.code,
    authorDecoration?.avatarFrame?.assetJson,
  )?.frameUrl;
  const hasCardStyle = cardStyle && Object.keys(cardStyle).length > 0;

  return (
    <article
      className={`content-card${className ? ` ${className}` : ''}${
        onClick ? ' is-clickable' : ''
      }${isVideo ? ' is-video' : ''}${hasCardStyle ? ' is-decorated' : ''}`}
      style={hasCardStyle ? cardStyle : undefined}
      onClick={onClick}
      onMouseEnter={onIntent}
      onPointerDown={onIntent}
    >
      {showRankBadge ? (
        <div className="content-card__rank-group" aria-label={`第 ${rank} 名`}>
          <span className="content-card__rank">{rank}</span>
          {showHeat ? (
            <span className="content-card__rank-heat">
              热度 {Math.round(hotScore)}
            </span>
          ) : null}
        </div>
      ) : null}
      <header className="content-card__author">
        <div className="content-card__author-main">
          <ProfileUserLink
            accountId={author.accountId}
            nickname={author.nickname}
            avatar={author.avatar}
            avatarFrameUrl={frameUrl}
            size={36}
            showNickname={false}
            className="content-card__avatar"
          />
          <div className="content-card__author-meta">
            <ProfileUserLink
              accountId={author.accountId}
              nickname={author.nickname}
              avatar={author.avatar}
              showAvatar={false}
              size={0}
              className="content-card__nickname"
            />
            {createdAt && (
              <span className="content-card__time">
                {formatCardTime(createdAt)}
              </span>
            )}
          </div>
        </div>
        {authorExtra}
      </header>

      {title?.trim() ? <h3 className="content-card__title">{title}</h3> : null}

      {!isRepost && summaryText ? (
        <p className="content-card__summary">{summaryText}</p>
      ) : null}

      {isRepost && refPost ? (
        <div className="content-card__repost">
          <RepostBlock quote={content} refPost={refPost} />
        </div>
      ) : null}

      {showVideoCover && (
        <div className="content-card__images content-card__images--video">
          <VideoCover coverUrl={coverUrl || images[0]} videoUrl={videoUrl} />
        </div>
      )}

      {showGallery && <ImageStrip images={galleryImages} />}

      {showTitlePoster && (
        <div className="content-card__images content-card__images--poster">
          <TextCoverPoster title={title.trim()} />
        </div>
      )}

      {(tags.length > 0 ||
        gameTags.length > 0 ||
        showHeat ||
        viewCount != null ||
        commentCount != null ||
        likeCount != null ||
        onLikeClick) && (
        <footer className="content-card__footer">
          <div className="content-card__footer-row">
            <div className="content-card__tags">
              {showHeat && !showRankBadge ? (
                <span className="content-card__tag content-card__tag--hot">
                  热度 {Math.round(hotScore)}
                </span>
              ) : null}
              {tags.map((tag) => (
                <span
                  key={`${tag.text}-${tag.icon || ''}`}
                  className="content-card__tag"
                >
                  {tag.icon && (
                    <img
                      src={tag.icon}
                      alt=""
                      className="content-card__tag-icon"
                    />
                  )}
                  <span className="content-card__tag-text">{tag.text}</span>
                </span>
              ))}
              {gameTags.map((tag) => (
                <Link
                  key={tag.appId}
                  to={`/game/${tag.appId}`}
                  state={buildGameDetailNavigationState(location, {
                    appId: tag.appId,
                    name: tag.name,
                    coverUrl: tag.iconUrl,
                  })}
                  className="content-card__tag content-card__tag--game"
                  onClick={(event) => event.stopPropagation()}
                  onMouseEnter={() => void preloadGameDetail()}
                  onPointerDown={() => void preloadGameDetail()}
                >
                  {tag.iconUrl ? (
                    <img
                      src={tag.iconUrl}
                      alt=""
                      className="content-card__tag-icon"
                    />
                  ) : null}
                  <span className="content-card__tag-text">{tag.name}</span>
                </Link>
              ))}
            </div>

            <div className="content-card__actions">
              {viewCount != null && (
                <StatAction
                  kind="view"
                  count={viewCount}
                  className="content-card__action"
                />
              )}
              {commentCount != null && (
                <StatAction
                  kind="comment"
                  count={commentCount}
                  className="content-card__action"
                  stopPropagation
                  onClick={onCommentClick}
                />
              )}
              {(likeCount != null || onLikeClick) && (
                <StatAction
                  kind="like"
                  count={likeCount ?? 0}
                  active={liked}
                  disabled={data.likePending}
                  className="content-card__action"
                  stopPropagation
                  onClick={onLikeClick}
                />
              )}
            </div>
          </div>
        </footer>
      )}
    </article>
  );
};

export default memo(ContentCard);
