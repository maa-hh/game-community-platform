import React, {
  memo,
  useCallback,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import type { CSSProperties, FC, SyntheticEvent } from 'react';

import TextCoverPoster from '@/base-ui/TextCoverPoster';
import VideoCover from '@/base-ui/VideoCover';
import { resolveFeedCoverMaxHeight } from '@/constants/feedCardMedia';
import {
  resolveFeedCoverDisplay,
  type FeedCoverDisplayLayout,
} from '@/utils/feedCardCoverLayout';
import { resolveFeedTitlePosterSize } from '@/utils/generateTitlePoster';

import './style.less';

export interface FeedCardCoverProps {
  mode: 'poster' | 'image' | 'video';
  title?: string;
  coverUrl?: string;
  videoUrl?: string;
  className?: string;
  onImageError?: () => void;
}

const FeedCardCover: FC<FeedCardCoverProps> = ({
  mode,
  title,
  coverUrl,
  videoUrl,
  className,
  onImageError,
}) => {
  const mediaRef = useRef<HTMLDivElement>(null);
  const [containerWidth, setContainerWidth] = useState(0);
  const [imageLayout, setImageLayout] = useState<FeedCoverDisplayLayout | null>(
    null,
  );

  useLayoutEffect(() => {
    const node = mediaRef.current;
    if (!node) return undefined;

    const syncWidth = () => {
      const next = Math.round(node.clientWidth);
      if (next > 0) setContainerWidth(next);
    };

    syncWidth();
    const observer = new ResizeObserver(syncWidth);
    observer.observe(node);
    return () => observer.disconnect();
  }, []);

  const updateImageLayout = useCallback((img: HTMLImageElement) => {
    const width = mediaRef.current?.clientWidth ?? 0;
    if (!img.naturalWidth || !img.naturalHeight || width <= 0) return;
    setImageLayout(
      resolveFeedCoverDisplay(width, img.naturalWidth, img.naturalHeight),
    );
  }, []);

  useLayoutEffect(() => {
    if (mode !== 'image') {
      setImageLayout(null);
      return undefined;
    }

    const node = mediaRef.current;
    if (!node) return undefined;

    const img = node.querySelector('img');
    if (img?.complete && img.naturalWidth > 0) {
      updateImageLayout(img);
    }

    const observer = new ResizeObserver(() => {
      const current = node.querySelector('img');
      if (current?.complete && current.naturalWidth > 0) {
        updateImageLayout(current);
      }
    });
    observer.observe(node);
    return () => observer.disconnect();
  }, [mode, coverUrl, updateImageLayout]);

  const handleImageLoad = (event: SyntheticEvent<HTMLImageElement>) => {
    updateImageLayout(event.currentTarget);
  };

  const maxCoverHeight =
    containerWidth > 0 ? resolveFeedCoverMaxHeight(containerWidth) : undefined;
  const posterSize = useMemo(() => {
    if (mode !== 'poster' || containerWidth <= 0) return undefined;
    return resolveFeedTitlePosterSize(containerWidth, title || '');
  }, [containerWidth, mode, title]);

  const rootClass = ['feed-card-cover', `feed-card-cover--${mode}`, className]
    .filter(Boolean)
    .join(' ');

  const rootStyle = useMemo((): CSSProperties | undefined => {
    const style: CSSProperties & Record<string, string> = {};

    if (maxCoverHeight) {
      style['--feed-card-cover-max-h'] = `${maxCoverHeight}px`;
    }
    if (posterSize) {
      style.height = `${posterSize.height}px`;
    } else if (mode === 'image' && imageLayout) {
      style.height = `${imageLayout.displayHeight}px`;
    }

    return Object.keys(style).length > 0 ? style : undefined;
  }, [imageLayout, maxCoverHeight, mode, posterSize]);

  if (mode === 'poster') {
    return (
      <div className={rootClass} ref={mediaRef} style={rootStyle}>
        <TextCoverPoster title={title || ''} variant="feed" />
      </div>
    );
  }

  if (mode === 'video') {
    return (
      <div className={rootClass} ref={mediaRef} style={rootStyle}>
        <VideoCover
          coverUrl={coverUrl}
          videoUrl={videoUrl}
          className="feed-card-cover__video"
        />
      </div>
    );
  }

  return (
    <div
      ref={mediaRef}
      className={`${rootClass}${imageLayout?.mode === 'crop' ? ' is-crop' : ''}`}
      style={rootStyle}
    >
      {coverUrl ? (
        <img
          src={coverUrl}
          alt=""
          className="feed-card-cover__image"
          loading="lazy"
          draggable={false}
          onLoad={handleImageLoad}
          onError={onImageError}
        />
      ) : null}
    </div>
  );
};

export default memo(FeedCardCover);
