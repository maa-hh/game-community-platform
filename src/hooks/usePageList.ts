import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
} from 'react';

import type { IPageResult } from '@/service/types';

import { useInfiniteScroll } from './useInfiniteScroll';
import {
  getPageDataCache,
  setPageDataCache,
  subscribePageDataCache,
} from './pageDataCache';
import { mergeById } from '@/utils/mergeById';

export interface UsePageListOptions<T> {
  pageSize?: number;
  enabled?: boolean;
  cacheKey?: string;
  /** 依赖变化时重置并拉取第一页 */
  resetDeps?: unknown[];
  fetchPage: (page: number, size: number) => Promise<IPageResult<T>>;
  /** 分页回填主键；未提供时按原数组替换，适用于无稳定主键的特殊列表。 */
  getKey?: (item: T) => string | number | undefined;
}

interface PageListCache<T> {
  items: T[];
  page: number;
  total: number;
  hasMore: boolean;
}

export function usePageList<T>({
  pageSize = 20,
  enabled = true,
  cacheKey,
  resetDeps = [],
  fetchPage,
  getKey,
}: UsePageListOptions<T>) {
  const cached = cacheKey
    ? getPageDataCache<PageListCache<T>>(cacheKey)
    : undefined;
  const [items, setItems] = useState<T[]>(cached?.items ?? []);
  const [page, setPage] = useState(cached?.page ?? 1);
  const [total, setTotal] = useState(cached?.total ?? 0);
  const [loading, setLoading] = useState(enabled && !cached);
  const [loadingMore, setLoadingMore] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [hasMore, setHasMore] = useState(cached?.hasMore ?? true);
  const fetchRef = useRef(fetchPage);
  const requestSeqRef = useRef(0);
  const refreshSeqRef = useRef(0);
  const cacheInvalidatedRef = useRef(false);
  const hasLoadedRef = useRef(Boolean(cached));
  const [cacheVersion, setCacheVersion] = useState(0);
  // 当前 state 所属缓存键，避免关键词切换时短暂展示上一份结果。
  const [resolvedCacheKey, setResolvedCacheKey] = useState(cacheKey);

  useEffect(() => {
    if (!cacheKey) return undefined;
    return subscribePageDataCache(cacheKey, () => {
      cacheInvalidatedRef.current = true;
      setCacheVersion((version) => version + 1);
    });
  }, [cacheKey]);

  // reset/load 使用 layout effect，因此 fetchPage 也必须在 layout 阶段前更新；
  // 否则关键词切换时新请求会捕获上一关键词的闭包。
  useLayoutEffect(() => {
    fetchRef.current = fetchPage;
  }, [fetchPage]);

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
    setPageDataCache(cacheKey, { items, page, total, hasMore });
  }, [
    cacheKey,
    hasMore,
    items,
    loading,
    loadingMore,
    page,
    resolvedCacheKey,
    total,
  ]);

  const loadPage = useCallback(
    async (nextPage: number, append: boolean) => {
      if (!enabled) return;
      const requestSeq = ++requestSeqRef.current;
      if (append) {
        setLoadingMore(true);
      } else {
        setLoading(true);
      }
      try {
        const res = await fetchRef.current(nextPage, pageSize);
        if (requestSeq !== requestSeqRef.current) return;
        const list = res.data || [];
        const nextTotal = Number(res.total ?? 0);
        hasLoadedRef.current = true;
        setTotal(nextTotal);
        setPage(nextPage);
        setItems((prev) => {
          const next =
            append && getKey
              ? mergeById(prev, list, getKey)
              : append
                ? [...prev, ...list]
                : list;
          setHasMore(next.length < nextTotal && list.length > 0);
          return next;
        });
      } catch {
        if (requestSeq !== requestSeqRef.current) return;
        // 刷新失败时保留旧列表，避免用户点击刷新后页面先变空。
        throw new Error('page-list-load-failed');
      } finally {
        if (requestSeq !== requestSeqRef.current) return;
        setLoading(false);
        setLoadingMore(false);
      }
    },
    [enabled, getKey, pageSize],
  );

  const reload = useCallback(async () => {
    const refreshSeq = ++refreshSeqRef.current;
    setRefreshing(true);
    setHasMore(true);
    try {
      await loadPage(1, false);
    } finally {
      if (refreshSeq === refreshSeqRef.current) setRefreshing(false);
    }
  }, [loadPage]);

  const loadMore = useCallback(async () => {
    if (!hasMore || loading || loadingMore) return;
    await loadPage(page + 1, true);
  }, [hasMore, loading, loadingMore, loadPage, page]);

  useLayoutEffect(() => {
    // 新条件开始前即废弃旧请求，不能让旧响应覆盖新列表。
    requestSeqRef.current += 1;
    setResolvedCacheKey(cacheKey);

    if (!enabled) {
      hasLoadedRef.current = false;
      setItems([]);
      setPage(1);
      setTotal(0);
      setHasMore(true);
      setRefreshing(false);
      return;
    }
    // 关键词切换时必须继续执行 reset/load，不能只更新 key 后直接 return，
    // 否则新关键词不会发起请求，界面会一直保留上一关键词的列表。
    // 失效通知优先于缓存命中，确保监控到变化后一定重新请求。
    const invalidated = cacheInvalidatedRef.current;
    cacheInvalidatedRef.current = false;
    const cachedForKey =
      !invalidated && cacheKey
        ? getPageDataCache<PageListCache<T>>(cacheKey)
        : undefined;
    if (cachedForKey) {
      hasLoadedRef.current = true;
      setItems(cachedForKey.items);
      setPage(cachedForKey.page);
      setTotal(cachedForKey.total);
      setHasMore(cachedForKey.hasMore);
      setLoading(false);
      setLoadingMore(false);
      setRefreshing(false);
      return;
    }
    const keepVisibleItems = invalidated && resolvedCacheKey === cacheKey;
    hasLoadedRef.current = false;
    if (!keepVisibleItems) {
      setItems([]);
      setPage(1);
      setTotal(0);
      setHasMore(true);
    }
    void loadPage(1, false).catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- resetDeps 由调用方控制
  }, [cacheKey, cacheVersion, enabled, pageSize, ...resetDeps]);

  const { sentinelRef } = useInfiniteScroll({
    enabled: enabled && resolvedCacheKey === cacheKey && items.length > 0,
    hasMore,
    loading,
    loadingMore,
    onLoadMore: loadMore,
  });

  // cacheKey 在关键词切换的首次 render 中已经变化，但 layout effect 还没来得及
  // 切换到新 state。此时不能把旧列表当成新查询结果渲染出来。
  const keyChanged = resolvedCacheKey !== cacheKey;
  const visibleItems = keyChanged ? [] : items;

  return {
    items: visibleItems,
    total: keyChanged ? 0 : total,
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
