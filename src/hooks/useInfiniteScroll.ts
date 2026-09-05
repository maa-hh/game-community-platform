import { useEffect, useRef } from 'react';

export interface UseInfiniteScrollOptions {
  /** 是否启用观察（如 Tab 未激活时可关闭） */
  enabled?: boolean;
  hasMore: boolean;
  loading?: boolean;
  loadingMore?: boolean;
  onLoadMore: () => void | Promise<void>;
  rootMargin?: string;
}

/**
 * 列表触底加载：IntersectionObserver 监听 sentinel 进入视口时触发 onLoadMore。
 */
export function useInfiniteScroll({
  enabled = true,
  hasMore,
  loading = false,
  loadingMore = false,
  onLoadMore,
  rootMargin = '120px',
}: UseInfiniteScrollOptions) {
  const sentinelRef = useRef<HTMLDivElement | null>(null);
  const loadingRef = useRef(false);

  useEffect(() => {
    loadingRef.current = loading || loadingMore;
  }, [loading, loadingMore]);

  useEffect(() => {
    if (!enabled) return undefined;
    const el = sentinelRef.current;
    if (!el) return undefined;

    const observer = new IntersectionObserver(
      (entries) => {
        const hit = entries.some(
          (entry) =>
            entry.isIntersecting &&
            getComputedStyle(entry.target).visibility === 'visible',
        );
        if (!hit || !hasMore || loadingRef.current) return;
        void onLoadMore();
      },
      { rootMargin },
    );

    observer.observe(el);
    return () => observer.disconnect();
  }, [enabled, hasMore, onLoadMore, rootMargin]);

  return { sentinelRef };
}
