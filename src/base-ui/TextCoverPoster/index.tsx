import React, { memo, useLayoutEffect, useRef, useState } from 'react';
import type { FC } from 'react';

import { useTitlePosterUrl } from '@/hooks/useTitlePosterUrl';

import './style.less';

export interface ITextCoverPosterProps {
  title: string;
  className?: string;
  /** feed 瀑布流：按容器宽生成，铺满标准封面框 */
  variant?: 'default' | 'feed';
}

/** 无图封面：展示时用标题 + 品牌模板生成海报（不上传） */
const TextCoverPoster: FC<ITextCoverPosterProps> = ({
  title,
  className,
  variant = 'default',
}) => {
  const trimmed = title.trim();
  const rootRef = useRef<HTMLDivElement>(null);
  const [feedWidth, setFeedWidth] = useState(0);
  const isFeed = variant === 'feed';

  useLayoutEffect(() => {
    if (!isFeed) return undefined;

    const node = rootRef.current;
    if (!node) return undefined;

    const syncWidth = () => {
      const next = Math.round(node.clientWidth);
      if (next > 0) setFeedWidth(next);
    };

    syncWidth();
    const observer = new ResizeObserver(syncWidth);
    observer.observe(node);
    return () => observer.disconnect();
  }, [isFeed]);

  const defaultPosterUrl = useTitlePosterUrl(isFeed ? '' : trimmed);
  const feedPosterUrl = useTitlePosterUrl(trimmed, {
    feedWidth: isFeed ? feedWidth : undefined,
  });
  const posterUrl = isFeed ? feedPosterUrl : defaultPosterUrl;

  if (!trimmed) return null;

  return (
    <div
      ref={rootRef}
      className={`text-cover-poster text-cover-poster--${variant}${
        className ? ` ${className}` : ''
      }`}
      aria-hidden
    >
      {posterUrl ? (
        <img
          src={posterUrl}
          alt=""
          className="text-cover-poster__image"
          loading="lazy"
          draggable={false}
        />
      ) : (
        <p className="text-cover-poster__title">{trimmed}</p>
      )}
    </div>
  );
};

export default memo(TextCoverPoster);
