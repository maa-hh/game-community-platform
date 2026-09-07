import { useEffect, useRef } from 'react';

/**
 * 预加载距离：让列表进入倒数约两行时开始请求下一页。
 *
 * 统一使用底部 root margin，而不是四个方向都扩展，避免用户只是回到
 * 列表顶部时误触发加载。各列表也可以通过 rootMargin 覆盖这个默认值。
 */
export const INFINITE_SCROLL_PRELOAD_ROOT_MARGIN = '0px 0px 480px 0px';

export interface UseInfiniteScrollOptions {
  /** 是否启用观察（如 Tab 未激活时可关闭） */
  enabled?: boolean;
  hasMore: boolean;
  loading?: boolean;
  loadingMore?: boolean;
  onLoadMore: () => void | Promise<void>;
  /** 加载更多失败时通知调用方；不提供时也必须消费 Promise 拒绝，避免未捕获异常。 */
  onLoadMoreError?: (error: unknown) => void;
  /** 触发预加载的提前量，默认约为倒数两行的高度 */
  rootMargin?: string;
}

/**
 * 列表预加载：IntersectionObserver 在末尾哨兵进入提前量区域时触发 onLoadMore。
 */
export function useInfiniteScroll({
  enabled = true,
  hasMore,
  loading = false,
  loadingMore = false,
  onLoadMore,
  onLoadMoreError,
  rootMargin = INFINITE_SCROLL_PRELOAD_ROOT_MARGIN,
}: UseInfiniteScrollOptions) {
  const sentinelRef = useRef<HTMLDivElement | null>(null);
  const loadingRef = useRef(false);
  // 哨兵在视口内时，loading 状态变化会重建 observer。失败后先阻止
  // 同一次进入事件反复重试，等哨兵离开并再次进入后再允许加载。
  const loadErrorRef = useRef(false);

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
        if (!hit) {
          loadErrorRef.current = false;
          return;
        }
        if (!hasMore || loadingRef.current || loadErrorRef.current) return;
        void (async () => {
          try {
            await onLoadMore();
          } catch (error) {
            loadErrorRef.current = true;
            onLoadMoreError?.(error);
          }
        })();
      },
      { rootMargin },
    );

    observer.observe(el);
    return () => observer.disconnect();
  }, [enabled, hasMore, onLoadMore, onLoadMoreError, rootMargin]);

  return { sentinelRef };
}
