import React, { memo, useCallback, useEffect, useMemo, useState } from 'react';
import type { FC, ReactNode } from 'react';

import {
  isLowResolutionSteamCover,
  steamCoverCandidates,
} from '@/utils/steamImage';

const MAX_RESOLVED_COVER_CACHE_SIZE = 500;
const resolvedCoverUrlCache = new Map<string, string>();

function buildCoverCacheKey(
  appId: number,
  coverUrl?: string | null,
  iconUrl?: string | null,
): string {
  return [appId, coverUrl?.trim() ?? '', iconUrl?.trim() ?? ''].join('|');
}

function rememberResolvedCover(cacheKey: string, url: string): void {
  // Map 同时承担简单的 LRU 顺序：再次命中时移动到末尾。
  resolvedCoverUrlCache.delete(cacheKey);
  resolvedCoverUrlCache.set(cacheKey, url);
  if (resolvedCoverUrlCache.size <= MAX_RESOLVED_COVER_CACHE_SIZE) return;

  const oldestKey = resolvedCoverUrlCache.keys().next().value;
  if (oldestKey) resolvedCoverUrlCache.delete(oldestKey);
}

export interface SteamCoverImageProps {
  appId: number;
  name: string;
  coverUrl?: string | null;
  iconUrl?: string | null;
  className?: string;
  placeholderClassName?: string;
  fallback?: ReactNode;
}

/** Steam 封面图：头图失败时自动回退到库 icon 等候选地址 */
const SteamCoverImage: FC<SteamCoverImageProps> = ({
  appId,
  name,
  coverUrl,
  iconUrl,
  className,
  placeholderClassName,
  fallback,
}) => {
  const cacheKey = useMemo(
    () => buildCoverCacheKey(appId, coverUrl, iconUrl),
    [appId, coverUrl, iconUrl],
  );
  const resolvedCoverUrl = resolvedCoverUrlCache.get(cacheKey);
  const candidates = useMemo(() => {
    const next = steamCoverCandidates(appId, coverUrl, iconUrl);
    if (!resolvedCoverUrl || !next.includes(resolvedCoverUrl)) return next;
    // 低清兜底只代表上一次高清地址曾加载失败，不能跨页面一直抢占高清候选。
    // 否则进入详情后换了 cache key 才会变清晰，返回列表时就会看到封面突变。
    if (isLowResolutionSteamCover(resolvedCoverUrl)) return next;
    return [
      resolvedCoverUrl,
      ...next.filter((item) => item !== resolvedCoverUrl),
    ];
  }, [appId, coverUrl, iconUrl, resolvedCoverUrl]);
  const [index, setIndex] = useState(0);

  useEffect(() => {
    setIndex(0);
  }, [candidates]);

  const handleError = useCallback(() => {
    if (resolvedCoverUrlCache.get(cacheKey) === candidates[index]) {
      resolvedCoverUrlCache.delete(cacheKey);
    }
    setIndex((current) => current + 1);
  }, [cacheKey, candidates, index]);

  const handleLoad = useCallback(() => {
    const loadedUrl = candidates[index];
    if (loadedUrl) rememberResolvedCover(cacheKey, loadedUrl);
  }, [cacheKey, candidates, index]);

  const src = index < candidates.length ? candidates[index] : undefined;
  const placeholderClass = placeholderClassName ?? className;

  if (!src) {
    if (fallback) {
      return <>{fallback}</>;
    }
    return (
      <span
        className={
          placeholderClass
            ? `${placeholderClass} steam-cover-image--placeholder`
            : 'steam-cover-image--placeholder'
        }
        aria-hidden
      >
        {name.slice(0, 1)}
      </span>
    );
  }

  return (
    <img
      src={src}
      alt={name}
      className={className}
      loading={resolvedCoverUrl ? 'eager' : 'lazy'}
      onLoad={handleLoad}
      onError={handleError}
    />
  );
};

export default memo(SteamCoverImage);
