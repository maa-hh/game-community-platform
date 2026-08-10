import React, { memo, useMemo, useState } from 'react';
import type { CSSProperties, FC, KeyboardEvent } from 'react';

import TextCoverPoster from '@/base-ui/TextCoverPoster';
import VideoCover from '@/base-ui/VideoCover';
import { FEED_ROW_POSTER_WIDTH } from '@/constants/feedCardMedia';
import { resolveFeedTitlePosterSize } from '@/utils/generateTitlePoster';
import {
  hasRealCoverMedia,
  resolvePostCardTitle,
  resolvePostCoverMedia,
} from '@/utils/postCover';

import type { PostRowPreviewProps } from './types';

import './style.less';

const PostRowPreview: FC<PostRowPreviewProps> = ({
  data,
  className,
  onClick,
  rank,
  metaExtra,
  metaFooter,
  showTime = true,
}) => {
  const [coverError, setCoverError] = useState(false);
  const coverMedia = resolvePostCoverMedia(data);
  const { isVideo, coverUrl, videoUrl, posterTitle } = coverMedia;
  const title = resolvePostCardTitle(data);
  const usePoster =
    !isVideo &&
    (!hasRealCoverMedia({ isVideo, coverUrl, coverError }) || !coverUrl);
  const posterSize = useMemo(() => {
    if (!usePoster) return undefined;
    return resolveFeedTitlePosterSize(FEED_ROW_POSTER_WIDTH, posterTitle);
  }, [posterTitle, usePoster]);
  const coverStyle = useMemo((): CSSProperties | undefined => {
    if (posterSize) return { height: posterSize.height };
    return undefined;
  }, [posterSize]);

  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (!onClick) return;
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      onClick();
    }
  };

  const rootClass = [
    'post-row-preview',
    onClick ? 'is-clickable' : '',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div
      className={rootClass}
      onClick={onClick}
      onKeyDown={handleKeyDown}
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
    >
      <div
        className={`post-row-preview__cover${
          usePoster ? ' post-row-preview__cover--poster' : ''
        }`}
        style={coverStyle}
        aria-hidden
      >
        {rank != null && rank > 0 ? (
          <span
            className={`post-row-preview__rank${
              rank <= 3
                ? ` post-row-preview__rank--${rank}`
                : ' post-row-preview__rank--default'
            }`}
          >
            {rank}
          </span>
        ) : null}
        {isVideo ? (
          <VideoCover
            coverUrl={coverUrl}
            videoUrl={videoUrl}
            className="post-row-preview__video"
          />
        ) : usePoster ? (
          posterTitle.trim() ? (
            <TextCoverPoster title={posterTitle} variant="feed" />
          ) : (
            <div className="post-row-preview__placeholder" />
          )
        ) : (
          <img
            src={coverUrl}
            alt=""
            className="post-row-preview__image"
            loading="lazy"
            draggable={false}
            onError={() => setCoverError(true)}
          />
        )}
      </div>

      <div className="post-row-preview__meta">
        <h3 className="post-row-preview__title" title={title}>
          {title}
        </h3>
        {metaExtra ? (
          <div className="post-row-preview__extra">{metaExtra}</div>
        ) : null}
        {showTime && data.createdAt ? (
          <time className="post-row-preview__time">{data.createdAt}</time>
        ) : null}
        {metaFooter ? (
          <div className="post-row-preview__footer">{metaFooter}</div>
        ) : null}
      </div>
    </div>
  );
};

export default memo(PostRowPreview);

export type { PostRowPreviewData, PostRowPreviewProps } from './types';
