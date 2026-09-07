import { useCallback, useEffect, useRef, useState } from 'react';
import { message } from 'antd';
import { useSearchParams } from 'react-router-dom';

import { fetchGameDiscoverApi } from '@/service/game';
import {
  fetchMyFollowedGamesApi,
  importSteamGamesToFollowsApi,
} from '@/service/userGame';
import { fetchSteamProfileApi } from '@/service/steam';
import type {
  GameDiscoverBoard,
  GameDiscoverOrder,
  GameDiscoverSort,
  IGameDiscoverQuery,
  IGameListItem,
  ISteamProfile,
  IUserGameItem,
} from '@/types/game';
import { formatApiError } from '@/utils/apiError';
import { useAppSelector } from '@/store';
import {
  getPageDataCache,
  invalidatePageDataCache,
  setPageDataCache,
  subscribePageDataCache,
} from '@/hooks/pageDataCache';
import { usePageRefresh } from '@/hooks/usePageRefresh';

export type GamesTabKey = 'mine' | 'discover';

const GAMES_TABS: GamesTabKey[] = ['mine', 'discover'];
const PAGE_SIZE = 18;

const DEFAULT_FILTERS: Omit<
  IGameDiscoverQuery,
  'board' | 'sort' | 'order' | 'page' | 'size'
> = {};

const DISCOVER_BOARDS: GameDiscoverBoard[] = [
  'all',
  'hot',
  'new',
  'free',
  'discount',
];

interface GamesDiscoverCache {
  items: IGameListItem[];
  page: number;
  total: number;
}

interface GamesMineCache {
  games: IUserGameItem[];
  steamBound: boolean;
  steamProfile?: ISteamProfile | null;
}

function buildDiscoverCacheKey(
  board: GameDiscoverBoard,
  sort: GameDiscoverSort,
  order: GameDiscoverOrder,
  filters: typeof DEFAULT_FILTERS,
  page: number,
) {
  return `${board}:${sort}:${order}:${page}:${JSON.stringify(filters)}`;
}

function parseGamesTab(value: string | null, isLoggedIn: boolean): GamesTabKey {
  if (value && GAMES_TABS.includes(value as GamesTabKey)) {
    return value as GamesTabKey;
  }
  return isLoggedIn ? 'mine' : 'discover';
}

function parseDiscoverBoard(value: string | null): GameDiscoverBoard {
  return value && DISCOVER_BOARDS.includes(value as GameDiscoverBoard)
    ? (value as GameDiscoverBoard)
    : 'all';
}

function parseDiscoverPage(value: string | null): number {
  const page = Number(value);
  return Number.isInteger(page) && page >= 1 ? page : 1;
}

function defaultSortForBoard(board: GameDiscoverBoard): GameDiscoverSort {
  return board === 'all' ? 'steam_score' : 'rank';
}

function defaultOrderForSort(sort: GameDiscoverSort): GameDiscoverOrder {
  return sort === 'rank' ? 'asc' : 'desc';
}

export function useGamesPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const accountId = useAppSelector((state) => state.auth.user?.accountId);
  const isLoggedIn = Boolean(accountId);
  const mineCacheKey = String(accountId ?? 'anonymous');
  const minePageDataCacheKey = `games:mine:${mineCacheKey}`;
  const initialMineCache =
    getPageDataCache<GamesMineCache>(minePageDataCacheKey);
  const activeTab = parseGamesTab(searchParams.get('tab'), isLoggedIn);
  const initialDiscoverBoard = parseDiscoverBoard(searchParams.get('board'));
  const initialDiscoverPage = parseDiscoverPage(searchParams.get('page'));
  const initialDiscoverSort = defaultSortForBoard(initialDiscoverBoard);
  const initialDiscoverOrder = defaultOrderForSort(initialDiscoverSort);
  const initialDiscoverCache = getPageDataCache<GamesDiscoverCache>(
    `games:discover:${buildDiscoverCacheKey(
      initialDiscoverBoard,
      initialDiscoverSort,
      initialDiscoverOrder,
      DEFAULT_FILTERS,
      initialDiscoverPage,
    )}`,
  );

  const setActiveTab = useCallback(
    (tab: GamesTabKey) => {
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          next.set('tab', tab);
          return next;
        },
        { replace: true, preventScrollReset: true },
      );
    },
    [setSearchParams],
  );

  useEffect(() => {
    if (isLoggedIn || searchParams.get('tab')) return;
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        next.set('tab', 'discover');
        return next;
      },
      { replace: true, preventScrollReset: true },
    );
  }, [isLoggedIn, searchParams, setSearchParams]);

  const [myGames, setMyGames] = useState<IUserGameItem[]>(
    initialMineCache?.games ?? [],
  );
  const [myLoading, setMyLoading] = useState(
    activeTab === 'mine' && !initialMineCache,
  );
  const [steamBound, setSteamBound] = useState(
    initialMineCache?.steamBound ?? false,
  );
  const [steamProfile, setSteamProfile] = useState<ISteamProfile | null>(
    initialMineCache?.steamProfile ?? null,
  );
  const [importing, setImporting] = useState(false);
  const [mineCacheVersion, setMineCacheVersion] = useState(0);

  useEffect(() => {
    return subscribePageDataCache(minePageDataCacheKey, () => {
      setMineCacheVersion((version) => version + 1);
    });
  }, [minePageDataCacheKey]);

  const [discoverItems, setDiscoverItems] = useState<IGameListItem[]>(
    initialDiscoverCache?.items ?? [],
  );
  const [discoverLoading, setDiscoverLoading] = useState(
    activeTab === 'discover' && !initialDiscoverCache,
  );
  const [discoverBoard, setDiscoverBoard] = useState<GameDiscoverBoard>(
    () => initialDiscoverBoard,
  );
  const [discoverSort, setDiscoverSort] =
    useState<GameDiscoverSort>(initialDiscoverSort);
  const [discoverOrder, setDiscoverOrder] =
    useState<GameDiscoverOrder>(initialDiscoverOrder);
  const [discoverFilters, setDiscoverFilters] = useState(DEFAULT_FILTERS);
  const [discoverPage, setDiscoverPage] = useState(
    initialDiscoverCache?.page ?? initialDiscoverPage,
  );
  const [discoverTotal, setDiscoverTotal] = useState(
    initialDiscoverCache?.total ?? 0,
  );
  const discoverLoadSeqRef = useRef(0);

  usePageRefresh(
    () =>
      activeTab === 'mine'
        ? loadMyGames()
        : loadDiscover({
            board: discoverBoard,
            sort: discoverSort,
            order: discoverOrder,
            filters: discoverFilters,
            page: discoverPage,
          }),
    Boolean(isLoggedIn || activeTab === 'discover'),
  );

  const loadMyGames = useCallback(async () => {
    if (!isLoggedIn) {
      setMyGames([]);
      setSteamBound(false);
      setSteamProfile(null);
      setMyLoading(false);
      return;
    }
    setMyLoading(true);
    try {
      const [profileRes, gamesRes] = await Promise.all([
        fetchSteamProfileApi().catch(() => ({ data: null })),
        fetchMyFollowedGamesApi(),
      ]);
      const profile = profileRes.data;
      const bound = Boolean(profile?.steamId || profile?.bound);
      setSteamProfile(profile);
      setSteamBound(bound);
      setMyGames(gamesRes.data || []);
      setPageDataCache<GamesMineCache>(minePageDataCacheKey, {
        games: gamesRes.data || [],
        steamBound: bound,
        steamProfile: profile,
      });
    } catch (err) {
      message.error(formatApiError('加载我的游戏失败', err));
    } finally {
      setMyLoading(false);
    }
  }, [isLoggedIn, minePageDataCacheKey]);

  const invalidateMineCache = useCallback(() => {
    invalidatePageDataCache(minePageDataCacheKey);
  }, [minePageDataCacheKey]);

  const loadDiscover = useCallback(
    async (options?: {
      board?: GameDiscoverBoard;
      sort?: GameDiscoverSort;
      order?: GameDiscoverOrder;
      filters?: typeof discoverFilters;
      page?: number;
    }) => {
      const board = options?.board ?? discoverBoard;
      const sort = options?.sort ?? discoverSort;
      const order = options?.order ?? discoverOrder;
      const filters = options?.filters ?? discoverFilters;
      const page = options?.page ?? discoverPage;
      const loadSeq = ++discoverLoadSeqRef.current;

      setDiscoverLoading(true);
      try {
        let res = await fetchGameDiscoverApi({
          board,
          sort,
          order,
          page,
          size: PAGE_SIZE,
          ...filters,
        });
        for (let attempt = 0; attempt < 5; attempt += 1) {
          const pageIsIncomplete =
            page > 1 && (res.data?.length ?? 0) < PAGE_SIZE;
          if (!res.expanding || !pageIsIncomplete) break;
          await new Promise((resolve) => window.setTimeout(resolve, 600));
          if (loadSeq !== discoverLoadSeqRef.current) return;
          res = await fetchGameDiscoverApi({
            board,
            sort,
            order,
            page,
            size: PAGE_SIZE,
            ...filters,
          });
        }
        if (loadSeq !== discoverLoadSeqRef.current) return;
        setDiscoverItems(res.data || []);
        setDiscoverPage(res.page || page);
        setDiscoverTotal(res.total || 0);
        if (!res.expanding || (res.data?.length ?? 0) >= PAGE_SIZE) {
          setPageDataCache(
            `games:discover:${buildDiscoverCacheKey(board, sort, order, filters, page)}`,
            {
              items: res.data || [],
              page: res.page || page,
              total: res.total || 0,
            },
          );
        }
      } catch (err) {
        if (loadSeq !== discoverLoadSeqRef.current) return;
        message.error(formatApiError('加载游戏列表失败', err));
      } finally {
        if (loadSeq === discoverLoadSeqRef.current) {
          setDiscoverLoading(false);
        }
      }
    },
    [discoverBoard, discoverFilters, discoverOrder, discoverPage, discoverSort],
  );

  useEffect(() => {
    if (activeTab === 'mine' && isLoggedIn) {
      const cachedMine = getPageDataCache<GamesMineCache>(minePageDataCacheKey);
      if (cachedMine) {
        setMyGames(cachedMine.games);
        setSteamBound(cachedMine.steamBound);
        setSteamProfile(cachedMine.steamProfile ?? null);
        setMyLoading(false);
        // 兼容没有 Steam 资料缓存的旧页面缓存，补一次资料请求以显示头像。
        if (cachedMine.steamProfile === undefined) {
          void loadMyGames();
        }
        return;
      }
      void loadMyGames();
    }
  }, [
    activeTab,
    isLoggedIn,
    loadMyGames,
    mineCacheVersion,
    minePageDataCacheKey,
  ]);

  useEffect(() => {
    if (!isLoggedIn) return undefined;
    const reloadSteamProfile = () => {
      invalidateMineCache();
    };
    window.addEventListener('steam-bind-success', reloadSteamProfile);
    window.addEventListener('steam-profile-changed', reloadSteamProfile);
    return () => {
      window.removeEventListener('steam-bind-success', reloadSteamProfile);
      window.removeEventListener('steam-profile-changed', reloadSteamProfile);
    };
  }, [invalidateMineCache, isLoggedIn]);

  useEffect(() => {
    if (activeTab === 'discover') {
      const cached = getPageDataCache<GamesDiscoverCache>(
        `games:discover:${buildDiscoverCacheKey(
          discoverBoard,
          discoverSort,
          discoverOrder,
          discoverFilters,
          discoverPage,
        )}`,
      );
      if (cached) {
        setDiscoverItems(cached.items);
        setDiscoverPage(cached.page);
        setDiscoverTotal(cached.total);
        setDiscoverLoading(false);
        return;
      }
      void loadDiscover();
    }
  }, [
    activeTab,
    discoverBoard,
    discoverSort,
    discoverOrder,
    discoverFilters,
    discoverPage,
    loadDiscover,
  ]);

  const importSteam = useCallback(async () => {
    setImporting(true);
    try {
      const res = await importSteamGamesToFollowsApi();
      const count = res.data?.imported ?? 0;
      message.success(
        count > 0 ? `已导入 ${count} 款游戏` : '没有可导入的新游戏',
      );
      invalidateMineCache();
    } catch (err) {
      message.error(formatApiError('导入失败', err));
    } finally {
      setImporting(false);
    }
  }, [invalidateMineCache]);

  const changeDiscoverBoard = useCallback(
    (board: GameDiscoverBoard) => {
      const nextSort = defaultSortForBoard(board);
      setDiscoverBoard(board);
      setDiscoverSort(nextSort);
      setDiscoverOrder(defaultOrderForSort(nextSort));
      setDiscoverPage(1);
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          if (board === 'all') {
            next.delete('board');
          } else {
            next.set('board', board);
          }
          next.delete('page');
          return next;
        },
        { replace: true, preventScrollReset: true },
      );
    },
    [setSearchParams],
  );

  const resetDiscoverPage = useCallback(() => {
    setDiscoverPage(1);
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        next.delete('page');
        return next;
      },
      { replace: true, preventScrollReset: true },
    );
  }, [setSearchParams]);

  const changeDiscoverSort = useCallback(
    (sort: GameDiscoverSort) => {
      setDiscoverSort(sort);
      setDiscoverOrder(defaultOrderForSort(sort));
      resetDiscoverPage();
    },
    [resetDiscoverPage],
  );

  const changeDiscoverOrder = useCallback(
    (order: GameDiscoverOrder) => {
      setDiscoverOrder(order);
      resetDiscoverPage();
    },
    [resetDiscoverPage],
  );

  const changeDiscoverFilters = useCallback(
    (filters: typeof discoverFilters) => {
      setDiscoverFilters(filters);
      resetDiscoverPage();
    },
    [resetDiscoverPage],
  );

  const changeDiscoverPage = useCallback(
    (page: number) => {
      setDiscoverPage(page);
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          if (page <= 1) {
            next.delete('page');
          } else {
            next.set('page', String(page));
          }
          return next;
        },
        { replace: true, preventScrollReset: true },
      );
      window.scrollTo({ top: 0, behavior: 'smooth' });
    },
    [setSearchParams],
  );

  return {
    activeTab,
    setActiveTab,
    myGames,
    myLoading,
    steamBound,
    steamProfile,
    importing,
    loadMyGames,
    invalidateMineCache,
    importSteam,
    discoverItems,
    discoverLoading,
    discoverBoard,
    discoverSort,
    discoverOrder,
    discoverFilters,
    discoverPage,
    discoverTotal,
    pageSize: PAGE_SIZE,
    changeDiscoverBoard,
    changeDiscoverSort,
    changeDiscoverOrder,
    changeDiscoverFilters,
    changeDiscoverPage,
  };
}
