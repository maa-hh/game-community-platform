import { useEffect, useMemo, useState } from 'react';

import {
  resolveDefaultTitlePosterSize,
  resolveFeedTitlePosterSize,
  generateTitlePosterBlob,
  type TitlePosterSize,
} from '@/utils/generateTitlePoster';

const posterCache = new Map<string, string>();
const CACHE_VERSION = 'feed-v8';
const MAX_CACHE_SIZE = 96;

export interface UseTitlePosterUrlOptions {
  /** 瀑布流：按容器宽 + 标题内容计算 2:1~3:4 海报尺寸 */
  feedWidth?: number;
}

function buildCacheKey(title: string, size?: TitlePosterSize): string {
  if (size) {
    return `${CACHE_VERSION}:${size.width}x${size.height}:${title.trim()}`;
  }
  return `${CACHE_VERSION}:default:${title.trim()}`;
}

async function resolveTitlePosterUrl(
  title: string,
  size?: TitlePosterSize,
): Promise<string> {
  const key = buildCacheKey(title, size);
  const cached = posterCache.get(key);
  if (cached) return cached;

  const blob = await generateTitlePosterBlob(title.trim(), size);
  const url = URL.createObjectURL(blob);
  posterCache.set(key, url);

  if (posterCache.size > MAX_CACHE_SIZE) {
    const oldest = posterCache.keys().next().value;
    if (oldest) {
      const stale = posterCache.get(oldest);
      if (stale) URL.revokeObjectURL(stale);
      posterCache.delete(oldest);
    }
  }

  return url;
}

/** 展示侧：按标题生成品牌封面图（仅前端渲染，不上传） */
export function useTitlePosterUrl(
  title: string,
  options?: UseTitlePosterUrlOptions,
): string | undefined {
  const trimmed = title.trim();
  const feedWidth = options?.feedWidth ?? 0;
  const feedSize = useMemo(
    () =>
      feedWidth > 0 && trimmed
        ? resolveFeedTitlePosterSize(feedWidth, trimmed)
        : undefined,
    [feedWidth, trimmed],
  );
  const defaultSize = useMemo(
    () => (trimmed ? resolveDefaultTitlePosterSize(trimmed) : undefined),
    [trimmed],
  );
  const posterSize = feedSize ?? defaultSize;
  const cacheKey = useMemo(() => {
    if (!trimmed || !posterSize) return '';
    return buildCacheKey(trimmed, posterSize);
  }, [trimmed, posterSize]);
  const [url, setUrl] = useState<string | undefined>(() =>
    cacheKey ? posterCache.get(cacheKey) : undefined,
  );

  useEffect(() => {
    if (!trimmed || !posterSize) {
      setUrl(undefined);
      return undefined;
    }

    if (options?.feedWidth !== undefined && feedWidth <= 0) {
      setUrl(undefined);
      return undefined;
    }

    const cached = posterCache.get(cacheKey);
    if (cached) {
      setUrl(cached);
      return undefined;
    }

    let cancelled = false;
    void resolveTitlePosterUrl(trimmed, posterSize)
      .then((next) => {
        if (!cancelled) setUrl(next);
      })
      .catch(() => {
        if (!cancelled) setUrl(undefined);
      });

    return () => {
      cancelled = true;
    };
  }, [trimmed, cacheKey, posterSize, feedWidth, options?.feedWidth]);

  return url;
}
