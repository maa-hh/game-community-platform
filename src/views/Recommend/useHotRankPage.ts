import { useCallback, useEffect, useRef, useState } from 'react';
import dayjs, { type Dayjs } from 'dayjs';
import { useSearchParams } from 'react-router-dom';

import { getPageDataCache, setPageDataCache } from '@/hooks/pageDataCache';
import { useFeedItemLike } from '@/hooks/useFeedItemLike';
import {
  createHotRankEventSource,
  fetchHotRankApi,
  mapHotRankItemsToLatest,
  type HotRankBoard,
  type IHotRankSsePayload,
} from '@/service/hotRank';
import { listCategoriesApi, type ICategory } from '@/service/content';
import type { LatestPostItem } from '@/types/post';
import {
  defaultPeriodDate,
  formatPeriodKey,
  normalizePeriodDate,
} from '@/utils/hotRankPeriod';

const SSE_DEBOUNCE_MS = 300;

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
    searchParams.get('period') && dayjs(searchParams.get('period')).isValid()
      ? normalizePeriodDate(initialBoard, dayjs(searchParams.get('period')))
      : defaultPeriodDate(initialBoard);
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
  const [error, setError] = useState<string | null>(null);

  const periodKey = formatPeriodKey(board, periodDate);
  const todayKey = dayjs().format('YYYY-MM-DD');
  /** 仅「日榜 + 今天」需要实时 SSE */
  const isLiveDaily = board === 'daily' && periodKey === todayKey;
  /** recommend-service 当前实现只支持主动拉取，旧 SSE 接口已废弃。 */
  const sseEnabled = false;

  const loadSeqRef = useRef(0);
  const sseDebounceRef = useRef<number | null>(null);
  const eventSourceRef = useRef<EventSource | null>(null);
  const likeInflightRef = useRef(0);
  const pendingSseReloadRef = useRef(false);

  const cacheKey = `recommend:${board}:${categoryId ?? 'all'}:${periodKey}`;

  useEffect(() => {
    const nextBoard = parseBoard(searchParams.get('tab'));
    setBoard(nextBoard);
    setCategoryId(parseCategoryId(searchParams.get('category')));
    const periodParam = searchParams.get('period');
    setPeriodDate(
      periodParam && dayjs(periodParam).isValid()
        ? normalizePeriodDate(nextBoard, dayjs(periodParam))
        : defaultPeriodDate(nextBoard),
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
        setItems([]);
      } finally {
        if (seq === loadSeqRef.current && !options?.silent) {
          setLoading(false);
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
    }
    void loadRank({ refresh: isLiveDaily, silent: Boolean(cachedItems) });
  }, [board, cacheKey, categoryId, periodKey, isLiveDaily, loadRank]);

  useEffect(() => {
    if (!loading && items.length > 0) {
      setPageDataCache(cacheKey, items);
    }
  }, [cacheKey, items, loading]);

  useEffect(() => {
    const disconnect = () => {
      eventSourceRef.current?.close();
      eventSourceRef.current = null;
      if (sseDebounceRef.current != null) {
        window.clearTimeout(sseDebounceRef.current);
        sseDebounceRef.current = null;
      }
    };

    // 非日榜今天 / 页面切到后台 / 离开推荐页卸载 → 不连 SSE
    if (!sseEnabled) {
      disconnect();
      return disconnect;
    }

    const source = createHotRankEventSource('daily', categoryId);
    eventSourceRef.current = source;

    const scheduleReload = () => {
      if (sseDebounceRef.current != null) return;
      sseDebounceRef.current = window.setTimeout(() => {
        sseDebounceRef.current = null;
        if (likeInflightRef.current > 0) {
          pendingSseReloadRef.current = true;
          return;
        }
        void loadRank({ refresh: false, silent: true });
      }, SSE_DEBOUNCE_MS);
    };

    const matchesCurrentChannel = (payload: IHotRankSsePayload) => {
      if (payload.board && payload.board !== 'daily') return false;
      if (payload.periodKey && payload.periodKey !== todayKey) return false;
      const payloadCategoryId = payload.categoryId ?? null;
      const activeCategoryId = categoryId ?? null;
      return payloadCategoryId === activeCategoryId;
    };

    const onPayload = (raw: string) => {
      if (!raw) return;
      try {
        const payload = JSON.parse(raw) as IHotRankSsePayload;
        if (!matchesCurrentChannel(payload)) return;
        scheduleReload();
      } catch {
        // ignore malformed SSE
      }
    };

    source.addEventListener('hot_rank_updated', (event) => {
      onPayload((event as MessageEvent<string>).data);
    });
    source.onmessage = (event) => onPayload(event.data);
    source.onerror = () => {
      source.close();
      if (eventSourceRef.current === source) {
        eventSourceRef.current = null;
      }
    };

    return disconnect;
  }, [categoryId, loadRank, sseEnabled, todayKey]);

  const handleLike = useFeedItemLike(setItems, {
    syncHotScore: true,
    inflightRef: likeInflightRef,
    onSettled: () => {
      if (likeInflightRef.current > 0) {
        pendingSseReloadRef.current = true;
        return;
      }
      pendingSseReloadRef.current = false;
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
    sseEnabled,
    categories,
    items,
    loading,
    error,
    loadRank,
    handleLike,
    onBoardChange,
    onCategoryChange,
    onPeriodChange,
  };
}
