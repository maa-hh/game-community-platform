import { useCallback, useEffect, useRef, useState } from 'react';
import dayjs, { type Dayjs } from 'dayjs';
import { useSearchParams } from 'react-router-dom';

import { getPageDataCache, setPageDataCache } from '@/hooks/pageDataCache';
import { useFeedItemLike } from '@/hooks/useFeedItemLike';
import {
  fetchHotRankApi,
  mapHotRankItemsToLatest,
  type HotRankBoard,
} from '@/service/hotRank';
import { listCategoriesApi, type ICategory } from '@/service/content';
import type { LatestPostItem } from '@/types/post';
import {
  defaultPeriodDate,
  formatPeriodKey,
  normalizePeriodDate,
  parsePeriodKey,
} from '@/utils/hotRankPeriod';

const HOT_RANK_BOARDS: HotRankBoard[] = ['total', 'weekly', 'daily'];

function parseBoard(value: string | null): HotRankBoard {
  return value && HOT_RANK_BOARDS.includes(value as HotRankBoard)
    ? (value as HotRankBoard)
    : 'total';
}

function parseCategoryId(value: string | null): number | undefined {
  if (!value) return undefined;
  const categoryId = Number(value);
  return Number.isInteger(categoryId) && categoryId > 0
    ? categoryId
    : undefined;
}

export function useHotRankPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const initialBoard = parseBoard(searchParams.get('tab'));
  const initialCategory = parseCategoryId(searchParams.get('category'));
  const initialPeriod =
    parsePeriodKey(initialBoard, searchParams.get('period')) ??
    defaultPeriodDate(initialBoard);
  const initialCacheKey = `recommend:${initialBoard}:${initialCategory ?? 'all'}:${formatPeriodKey(initialBoard, initialPeriod)}`;
  const initialCachedItems =
    getPageDataCache<LatestPostItem[]>(initialCacheKey);
  const [board, setBoard] = useState<HotRankBoard>(() => initialBoard);
  const [categoryId, setCategoryId] = useState<number | undefined>(
    () => initialCategory,
  );
  const [periodDate, setPeriodDate] = useState<Dayjs>(initialPeriod);
  const [categories, setCategories] = useState<ICategory[]>([]);
  const [items, setItems] = useState<LatestPostItem[]>(
    initialCachedItems ?? [],
  );
  const [loading, setLoading] = useState(!initialCachedItems);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const periodKey = formatPeriodKey(board, periodDate);
  const todayKey = dayjs().format('YYYY-MM-DD');
  /** 仅「日榜 + 今天」默认请求实时投影。 */
  const isLiveDaily = board === 'daily' && periodKey === todayKey;

  const loadSeqRef = useRef(0);
  const likeInflightRef = useRef(0);

  const cacheKey = `recommend:${board}:${categoryId ?? 'all'}:${periodKey}`;

  useEffect(() => {
    const nextBoard = parseBoard(searchParams.get('tab'));
    setBoard(nextBoard);
    setCategoryId(parseCategoryId(searchParams.get('category')));
    const periodParam = searchParams.get('period');
    setPeriodDate(
      parsePeriodKey(nextBoard, periodParam) ?? defaultPeriodDate(nextBoard),
    );
  }, [searchParams]);

  useEffect(() => {
    listCategoriesApi()
      .then((res) => setCategories(res.data || []))
      .catch(() => setCategories([]));
  }, []);

  const loadRank = useCallback(
    async (options?: { refresh?: boolean; silent?: boolean }) => {
      const seq = ++loadSeqRef.current;
      setRefreshing(true);
      if (!options?.silent) {
        setLoading(true);
        setError(null);
      }
      try {
        const rows = await fetchHotRankApi({
          board,
          categoryId,
          // 总榜 periodKey=截止日；日榜=当天；周榜=当周
          periodKey,
          refresh: options?.refresh ?? isLiveDaily,
        });
        if (seq !== loadSeqRef.current) return;
        const mappedItems = await mapHotRankItemsToLatest(rows);
        setItems(mappedItems);
        setPageDataCache(cacheKey, mappedItems);
      } catch (err) {
        if (seq !== loadSeqRef.current) return;
        setError(err instanceof Error ? err.message : '加载热榜失败');
      } finally {
        if (seq === loadSeqRef.current) {
          setRefreshing(false);
          if (!options?.silent) setLoading(false);
        }
      }
    },
    [board, cacheKey, categoryId, isLiveDaily, periodKey],
  );

  useEffect(() => {
    const cachedItems = getPageDataCache<LatestPostItem[]>(cacheKey);
    if (cachedItems) {
      setItems(cachedItems);
      setLoading(false);
      setRefreshing(false);
      return;
    }
    void loadRank({ refresh: false });
  }, [board, cacheKey, categoryId, periodKey, isLiveDaily, loadRank]);

  useEffect(() => {
    if (!loading && items.length > 0) {
      setPageDataCache(cacheKey, items);
    }
  }, [cacheKey, items, loading]);

  const handleLike = useFeedItemLike(setItems, {
    syncHotScore: true,
    inflightRef: likeInflightRef,
    onSettled: () => {
      if (likeInflightRef.current > 0) {
        return;
      }
      void loadRank({ refresh: true, silent: true });
      // 点赞先写业务库，再经 Outbox/Kafka 更新热榜，补拉一次避免读到事件落库前的结果。
      window.setTimeout(() => {
        void loadRank({ refresh: true, silent: true });
      }, 1000);
    },
  });

  const onBoardChange = (next: HotRankBoard) => {
    setBoard(next);
    setPeriodDate(defaultPeriodDate(next));
    const params = new URLSearchParams(searchParams);
    if (next === 'total') params.delete('tab');
    else params.set('tab', next);
    params.set('period', formatPeriodKey(next, defaultPeriodDate(next)));
    setSearchParams(params, { replace: true, preventScrollReset: true });
  };

  const onCategoryChange = (value: number | undefined) => {
    setCategoryId(value);
    const params = new URLSearchParams(searchParams);
    if (value == null) params.delete('category');
    else params.set('category', String(value));
    setSearchParams(params, { replace: true, preventScrollReset: true });
  };

  const onPeriodChange = (value: Dayjs | null) => {
    if (value) {
      const next = normalizePeriodDate(board, value);
      setPeriodDate(next);
      const params = new URLSearchParams(searchParams);
      params.set('period', formatPeriodKey(board, next));
      setSearchParams(params, { replace: true, preventScrollReset: true });
    }
  };

  return {
    board,
    categoryId,
    periodDate,
    periodKey,
    isLiveDaily,
    categories,
    items,
    loading,
    refreshing,
    error,
    loadRank,
    handleLike,
    onBoardChange,
    onCategoryChange,
    onPeriodChange,
  };
}
