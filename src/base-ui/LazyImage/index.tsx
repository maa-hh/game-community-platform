import React, { memo, useLayoutEffect, useRef, useState } from 'react';
import type { FC, ReactNode, SyntheticEvent } from 'react';
import { Spin } from 'antd';

import './style.less';

export interface LazyImageProps {
  src?: string | null;
  alt?: string;
  className?: string;
  imgClassName?: string;
  fallback?: ReactNode;
  loading?: 'lazy' | 'eager';
  draggable?: boolean;
  onLoad?: (event: SyntheticEvent<HTMLImageElement>) => void;
}

const LazyImage: FC<LazyImageProps> = ({
  src,
  alt = '',
  className,
  imgClassName,
  fallback = null,
  loading = 'lazy',
  draggable,
  onLoad,
}) => {
  const imageRef = useRef<HTMLImageElement>(null);
  const [loadedSrc, setLoadedSrc] = useState<string | null>(null);
  const [failedSrc, setFailedSrc] = useState<string | null>(null);
  const loaded = Boolean(src) && loadedSrc === src;
  const failed = !src || failedSrc === src;

  // 缓存图片可能在 React effect 前已经完成，主动读取 complete 避免漏掉 load 事件。
  useLayoutEffect(() => {
    const image = imageRef.current;
    if (!src || !image?.complete) return;
    if (image.naturalWidth > 0) {
      setLoadedSrc(src);
    } else {
      setFailedSrc(src);
    }
  }, [src]);

  if (!src || failed) {
    return (
      <div
        className={['lazy-image', 'lazy-image--fallback', className]
          .filter(Boolean)
          .join(' ')}
      >
        {fallback}
      </div>
    );
  }

  const rootClass = ['lazy-image', loaded ? 'is-loaded' : '', className]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={rootClass}>
      {!loaded ? (
        <div className="lazy-image__placeholder" aria-hidden>
          <Spin size="small" />
        </div>
      ) : null}
      <img
        key={src}
        ref={imageRef}
        src={src}
        alt={alt}
        className={['lazy-image__img', imgClassName].filter(Boolean).join(' ')}
        loading={loading}
        draggable={draggable}
        onLoad={(event) => {
          setLoadedSrc(src);
          onLoad?.(event);
        }}
        onError={() => setFailedSrc(src)}
      />
    </div>
  );
};

export default memo(LazyImage);
