import React, { memo } from 'react';
import type { FC } from 'react';

import ShareCardBody from './parts/ShareCardBody';
import ShareCardCover from './parts/ShareCardCover';
import type { IShareCardProps } from './types';
import { useShareCard } from './useShareCard';

import './style.less';

/** 站内转发引用盒（原帖摘要卡，点击进原帖详情） */
const ShareCard: FC<IShareCardProps> = (props) => {
  const { data, className, preview = false, suppressCover = false } = props;
  const {
    isVideo,
    cover,
    posterTitle,
    unavailable,
    viewCount,
    commentCount,
    likeCount,
    liked,
    handleClick,
    handleKeyDown,
  } = useShareCard(props);

  const coverNode =
    !suppressCover && !unavailable && (isVideo || cover || posterTitle) ? (
      <ShareCardCover
        isVideo={isVideo}
        cover={cover}
        videoUrl={data.videoUrl}
        posterTitle={posterTitle}
      />
    ) : null;

  return (
    <article
      className={`share-card${preview ? ' share-card--preview' : ''}${
        unavailable ? ' share-card--unavailable' : ''
      }${className ? ` ${className}` : ''}${coverNode ? ' has-cover' : ''}`}
      onClick={preview ? undefined : handleClick}
      role={preview ? 'presentation' : unavailable ? 'note' : 'link'}
      tabIndex={preview || unavailable ? undefined : 0}
      onKeyDown={preview || unavailable ? undefined : handleKeyDown}
      aria-label={unavailable ? data.unavailableMessage : undefined}
    >
      {coverNode && <div className="share-card__cover">{coverNode}</div>}
      <ShareCardBody
        data={data}
        viewCount={viewCount}
        commentCount={commentCount}
        likeCount={likeCount}
        liked={liked}
        unavailable={unavailable}
      />
    </article>
  );
};

export default memo(ShareCard);
