import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
} from 'react';

import { useInfiniteScroll } from './useInfiniteScroll';
import {
  getPageDataCache,
  setPageDataCache,
  subscribePageDataCache,
} from './pageDataCache';

export interface UseCursorListOptions<T> {
  pageSize?: number;
  enabled?: boolean;
  cacheKey?: string;
  initialItems?: T[];
  initialHasMore?: boolean;
  initialCursor?: string;
  skipInitialFetch?: boolean;
  resetDeps?: unknown[];
  /** 从最后一条记录提取游标（如 ISO 时间或 id） */
  getCursor: (item: T) => string | undefined;
  fetchBatch: (cursor: string | undefined, size: number) => Promise<T[]>;
  /** 自定义是否还有更多；默认 batch.length >= pageSize */
  hasMoreFromBatch?: (batch: T[], pageSize: number) => boolean;
}

interface CursorListCache<T> {
  items: T[];
  hasMore: boolean;
  cursor?: string;
}

export function useCursorList<T>({
  pageSize = 20,
  enabled = true,
  cacheKey,
  initialItems = [],
  initialHasMore = true,
  initialCursor,
  skipInitialFetch = false,
  resetDeps = [],
  getCursor,
  fetchBatch,
  hasMoreFromBatch,
}: UseCursorListOptions<T>) {
  const cache = cacheKey
    ? getPageDataCache<CursorListCache<T>>(cacheKey)
    : undefined;
  const [items, setItems] = useState<T[]>(cache?.items ?? initialItems);
  const [loading, setLoading] = useState(
    enabled && !skipInitialFetch && !cache,
  );
  const [loadingMore, setLoadingMore] = useState(false);
  const [hasMore, setHasMore] = useState(cache?.hasMore ?? initialHasMore);
  const cursorRef = useRef<string | undefined>(cache?.cursor ?? initialCursor);
  const fetchRef = useRef(fetchBatch);
  const requestSeqRef = useRef(0);
  const cacheKeyRef = useRef(cacheKey);
  const cacheInvalidatedRef = useRef(false);
  const [cacheVersion, setCacheVersion] = useState(0);

  useEffect(() => {
    if (!cacheKey) return undefined;
    return subscribePageDataCache(cacheKey, () => {
      cacheInvalidatedRef.current = true;
      setCacheVersion((version) => version + 1);
    });
  }, [cacheKey]);

  useEffect(() => {
    if (cacheKeyRef.current !== cacheKey) {
      cacheKeyRef.current = cacheKey;
      return;
    }
    if (
      !cacheKey ||
      cacheInvalidatedRef.current ||
      loading ||
      loadingMore ||
      items.length === 0
    )
      return;
    setPageDataCache(cacheKey, {
      items,
      hasMore,
      cursor: cursorRef.current,
    });
  }, [cacheKey, hasMore, items, loading, loadingMore]);

  useEffect(() => {
    fetchRef.current = fetchBatch;
  }, [fetchBatch]);

  const applyBatch = useCallback(
    (batch: T[], append: boolean) => {
      const more = hasMoreFromBatch
        ? hasMoreFromBatch(batch, pageSize)
        : batch.length >= pageSize;
      setHasMore(more);
      setItems((prev) => {
        const next = append ? [...prev, ...batch] : batch;
        const last = next[next.length - 1];
        cursorRef.current = last ? getCursor(last) : undefined;
        return next;
      });
    },
    [getCursor, hasMoreFromBatch, pageSize],
  );

  const loadBatch = useCallback(
    async (cursor: string | undefined, append: boolean) => {
      if (!enabled) return;
      const requestSeq = ++requestSeqRef.current;
      if (append) {
        setLoadingMore(true);
      } else {
        setLoading(true);
      }
      try {
        const batch = await fetchRef.current(cursor, pageSize);
        if (requestSeq !== requestSeqRef.current) return;
        applyBatch(batch, append);
      } catch {
        if (requestSeq !== requestSeqRef.current) return;
        if (!append) {
          setItems([]);
          setHasMore(false);
          cursorRef.current = undefined;
        }
        throw new Error('cursor-list-load-failed');
      } finally {
        if (requestSeq !== requestSeqRef.current) return;
        setLoading(false);
        setLoadingMore(false);
      }
    },
    [applyBatch, enabled, pageSize],
  );

  const reload = useCallback(async () => {
    cursorRef.current = undefined;
    setHasMore(true);
    await loadBatch(undefined, false);
  }, [loadBatch]);

  const loadMore = useCallback(async () => {
    if (!hasMore || loading || loadingMore) return;
    await loadBatch(cursorRef.current, true);
  }, [hasMore, loadBatch, loading, loadingMore]);

  useLayoutEffect(() => {
    if (!enabled) {
      requestSeqRef.current += 1;
      setItems([]);
      setHasMore(true);
      setLoading(false);
      cursorRef.current = undefined;
      return;
    }
    const invalidated = cacheInvalidatedRef.current;
    cacheInvalidatedRef.current = false;
    const cachedForKey =
      !invalidated && cacheKey
        ? getPageDataCache<CursorListCache<T>>(cacheKey)
        : undefined;
    if (cachedForKey) {
      requestSeqRef.current += 1;
      setItems(cachedForKey.items);
      setHasMore(cachedForKey.hasMore);
      setLoading(false);
      setLoadingMore(false);
      cursorRef.current = cachedForKey.cursor;
      return;
    }
    if (skipInitialFetch) return;
    setItems([]);
    setLoading(true);
    setLoadingMore(false);
    cursorRef.current = undefined;
    setHasMore(true);
    void loadBatch(undefined, false).catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- resetDeps 由调用方控制
  }, [
    cacheKey,
    cacheVersion,
    enabled,
    pageSize,
    skipInitialFetch,
    // eslint-disable-next-line react-hooks/exhaustive-deps -- resetDeps 由调用方控制
    ...resetDeps,
  ]);

  const { sentinelRef } = useInfiniteScroll({
    enabled: enabled && items.length > 0,
    hasMore,
    loading,
    loadingMore,
    onLoadMore: loadMore,
  });

  return {
    items,
    loading,
    loadingMore,
    hasMore,
    reload,
    loadMore,
    setItems,
    sentinelRef,
  };
}
