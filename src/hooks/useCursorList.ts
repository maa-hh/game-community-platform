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
import { mergeById } from '@/utils/mergeById';

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
  /** 增量回填主键；未提供时保持兼容的纯追加行为。 */
  getKey?: (item: T) => string | number | undefined;
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
  getKey,
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
  const [refreshing, setRefreshing] = useState(false);
  const [hasMore, setHasMore] = useState(cache?.hasMore ?? initialHasMore);
  const cursorRef = useRef<string | undefined>(cache?.cursor ?? initialCursor);
  const fetchRef = useRef(fetchBatch);
  const requestSeqRef = useRef(0);
  const refreshSeqRef = useRef(0);
  const hasLoadedRef = useRef(Boolean(cache));
  // 当前 state 对应的键。切换筛选的第一帧不得渲染上一份列表。
  const [resolvedCacheKey, setResolvedCacheKey] = useState(cacheKey);
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
    if (
      !cacheKey ||
      resolvedCacheKey !== cacheKey ||
      cacheInvalidatedRef.current ||
      loading ||
      loadingMore ||
      !hasLoadedRef.current
    )
      return;
    setPageDataCache(cacheKey, {
      items,
      hasMore,
      cursor: cursorRef.current,
    });
  }, [cacheKey, hasMore, items, loading, loadingMore, resolvedCacheKey]);

  // reset/load 会在 layout effect 中发起，因此请求闭包也要在同一阶段更新。
  useLayoutEffect(() => {
    fetchRef.current = fetchBatch;
  }, [fetchBatch]);

  const applyBatch = useCallback(
    (batch: T[], append: boolean) => {
      const more = hasMoreFromBatch
        ? hasMoreFromBatch(batch, pageSize)
        : batch.length >= pageSize;
      setHasMore(more);
      setItems((prev) => {
        const next =
          append && getKey
            ? mergeById(prev, batch, getKey)
            : append
              ? [...prev, ...batch]
              : batch;
        const last = next[next.length - 1];
        cursorRef.current = last ? getCursor(last) : undefined;
        return next;
      });
    },
    [getCursor, getKey, hasMoreFromBatch, pageSize],
  );

  const loadBatch = useCallback(
    async (cursor: string | undefined, append: boolean, silent = false) => {
      if (!enabled) return;
      const requestSeq = ++requestSeqRef.current;
      if (append) {
        setLoadingMore(true);
      } else if (!silent) {
        setLoading(true);
      }
      try {
        const batch = await fetchRef.current(cursor, pageSize);
        if (requestSeq !== requestSeqRef.current) return;
        applyBatch(batch, append);
        hasLoadedRef.current = true;
      } catch {
        if (requestSeq !== requestSeqRef.current) return;
        // 刷新失败时保留旧列表，避免用户点击刷新后页面先变空。
        throw new Error('cursor-list-load-failed');
      } finally {
        if (requestSeq !== requestSeqRef.current) return;
        // silent 只控制是否显示加载过程，不影响请求结束后的状态收敛。
        // 否则静默刷新会让首屏请求留下的 loading 永远保持为 true。
        setLoading(false);
        setLoadingMore(false);
      }
    },
    [applyBatch, enabled, pageSize],
  );

  const reload = useCallback(
    async (silent = false) => {
      const refreshSeq = ++refreshSeqRef.current;
      setRefreshing(true);
      // 刷新会废弃正在进行的加载更多请求，避免旧请求被作废后
      // 没有机会再清理 loadingMore。
      setLoadingMore(false);
      cursorRef.current = undefined;
      setHasMore(true);
      try {
        await loadBatch(undefined, false, silent);
      } finally {
        if (refreshSeq === refreshSeqRef.current) setRefreshing(false);
      }
    },
    [loadBatch],
  );

  const loadMore = useCallback(async () => {
    if (!hasMore || loading || loadingMore) return;
    await loadBatch(cursorRef.current, true);
  }, [hasMore, loadBatch, loading, loadingMore]);

  useLayoutEffect(() => {
    // 当前页面键变化后，使所有旧请求失效，避免旧结果回写到新筛选。
    requestSeqRef.current += 1;
    setResolvedCacheKey(cacheKey);

    if (!enabled) {
      hasLoadedRef.current = false;
      setItems([]);
      setHasMore(true);
      setLoading(false);
      setRefreshing(false);
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
      hasLoadedRef.current = true;
      setItems(cachedForKey.items);
      setHasMore(cachedForKey.hasMore);
      setLoading(false);
      setLoadingMore(false);
      setRefreshing(false);
      cursorRef.current = cachedForKey.cursor;
      return;
    }
    if (skipInitialFetch) {
      hasLoadedRef.current = false;
      setLoading(false);
      setLoadingMore(false);
      return;
    }
    const keepVisibleItems = invalidated && resolvedCacheKey === cacheKey;
    hasLoadedRef.current = false;
    if (!keepVisibleItems) setItems([]);
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
    enabled: enabled && resolvedCacheKey === cacheKey && items.length > 0,
    hasMore,
    loading,
    loadingMore,
    onLoadMore: loadMore,
  });

  const keyChanged = resolvedCacheKey !== cacheKey;

  return {
    items: keyChanged ? [] : items,
    loading: keyChanged ? enabled : loading,
    loadingMore: keyChanged ? false : loadingMore,
    refreshing: keyChanged ? false : refreshing,
    hasMore: keyChanged ? true : hasMore,
    reload,
    loadMore,
    setItems,
    sentinelRef,
  };
}
