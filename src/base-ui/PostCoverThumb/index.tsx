import React, { memo, useMemo, useState } from 'react';
import type { CSSProperties, FC } from 'react';

import TextCoverPoster from '@/base-ui/TextCoverPoster';
import VideoCover from '@/base-ui/VideoCover';
import {
  FEED_ROW_POSTER_WIDTH,
  NOTIFICATION_COVER_WIDTH,
  resolveFeedCoverMaxHeight,
} from '@/constants/feedCardMedia';
import { resolveFeedTitlePosterSize } from '@/utils/generateTitlePoster';
import {
  hasRealCoverMedia,
  resolvePostCoverMedia,
  type PostCoverSource,
} from '@/utils/postCover';

import './style.less';

export interface PostCoverThumbProps {
  source: PostCoverSource;
  className?: string;
}

/** 通知等场景：与推荐榜同款 128px 海报生成，等比缩放到展示宽 */
const PostCoverThumb: FC<PostCoverThumbProps> = ({ source, className }) => {
  const [coverError, setCoverError] = useState(false);
  const coverMedia = resolvePostCoverMedia(source);
  const { isVideo, coverUrl, videoUrl, posterTitle } = coverMedia;
  const usePoster =
    !isVideo &&
    (!hasRealCoverMedia({ isVideo, coverUrl, coverError }) || !coverUrl);

  const posterSize = useMemo(() => {
    if (!usePoster) return undefined;
    return resolveFeedTitlePosterSize(FEED_ROW_POSTER_WIDTH, posterTitle);
  }, [posterTitle, usePoster]);

  const posterScale = NOTIFICATION_COVER_WIDTH / FEED_ROW_POSTER_WIDTH;
  const maxImageHeight = resolveFeedCoverMaxHeight(NOTIFICATION_COVER_WIDTH);

  const rootClass = [
    'post-cover-thumb',
    usePoster ? 'post-cover-thumb--poster' : '',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  const rootStyle = useMemo((): CSSProperties | undefined => {
    if (usePoster && posterSize) {
      return {
        width: NOTIFICATION_COVER_WIDTH,
        height: Math.max(1, Math.round(posterSize.height * posterScale)),
      };
    }
    return {
      width: NOTIFICATION_COVER_WIDTH,
      maxHeight: maxImageHeight,
    };
  }, [maxImageHeight, posterScale, posterSize, usePoster]);

  const posterHostStyle = useMemo((): CSSProperties | undefined => {
    if (!posterSize) return undefined;
    return {
      width: FEED_ROW_POSTER_WIDTH,
      height: posterSize.height,
      transform: `scale(${posterScale})`,
    };
  }, [posterScale, posterSize]);

  return (
    <div className={rootClass} style={rootStyle} aria-hidden>
      {isVideo ? (
        <VideoCover
          coverUrl={coverUrl}
          videoUrl={videoUrl}
          className="post-cover-thumb__video"
        />
      ) : usePoster ? (
        posterTitle.trim() ? (
          <div
            className="post-cover-thumb__poster-host"
            style={posterHostStyle}
          >
            <TextCoverPoster title={posterTitle} variant="feed" />
          </div>
        ) : (
          <div className="post-cover-thumb__placeholder" />
        )
      ) : (
        <img
          src={coverUrl}
          alt=""
          className="post-cover-thumb__image"
          loading="lazy"
          draggable={false}
          onError={() => setCoverError(true)}
        />
      )}
    </div>
  );
};

export default memo(PostCoverThumb);
