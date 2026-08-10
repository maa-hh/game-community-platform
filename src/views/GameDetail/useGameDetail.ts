import { useCallback, useEffect, useRef, useState } from 'react';
import { message } from 'antd';
import { useSearchParams } from 'react-router-dom';

import { useCursorList } from '@/hooks/useCursorList';
import { useFeedItemLike } from '@/hooks/useFeedItemLike';
import {
  deleteMyGameReviewApi,
  fetchGameDetailApi,
  fetchGameDiscussionsApi,
  fetchGameReviewsApi,
  fetchMyGameReviewApi,
  saveMyGameReviewApi,
} from '@/service/game';
import {
  checkGameFollowApi,
  followGameApi,
  unfollowGameApi,
} from '@/service/userGame';
import {
  fetchSteamGameStatsApi,
  syncSteamGameAchievementsApi,
} from '@/service/steam';
import type {
  IGameDetail,
  IGameRatingStats,
  IGameReview,
  ISteamGameStats,
} from '@/types/game';
import type { GameReviewSort } from '@/types/game';
import { formatApiError } from '@/utils/apiError';
import { useAppSelector } from '@/store';
import { getPageDataCache, setPageDataCache } from '@/hooks/pageDataCache';

type GameDetailPayload = IGameDetail & {
  averageScore?: number;
  reviewCount?: number;
  scoreDistribution?: Record<string, number>;
};

function toRatingStats(game: GameDetailPayload): IGameRatingStats | null {
  if (game.averageScore == null && game.reviewCount == null) {
    return null;
  }
  return {
    appId: game.appId,
    averageScore: Number(game.averageScore ?? 0),
    reviewCount: Number(game.reviewCount ?? 0),
    distribution: game.scoreDistribution,
  };
}

/** 旧版本可能把空富详情写入页面缓存，空快照必须重新请求后端。 */
function hasRichGameDetail(detail?: IGameDetail | null): boolean {
  if (!detail) return false;
  const hasDescription = Boolean(
    detail.shortDescription?.trim() || detail.aboutHtml?.trim(),
  );
  const hasSupplement = Boolean(
    (detail.screenshots?.length ?? 0) > 0 ||
    (detail.movies?.length ?? 0) > 0 ||
    (detail.achievementHighlights?.length ?? 0) > 0 ||
    detail.achievementTotal != null ||
    detail.pcRequirementsMin?.trim() ||
    detail.pcRequirementsRec?.trim(),
  );
  return hasDescription && hasSupplement;
}

function isGameDetailReady(detail?: IGameDetail | null): boolean {
  if (!detail || detail.detailReady === false) return false;
  return hasRichGameDetail(detail);
}

const DISCUSSION_PAGE_SIZE = 20;
const REVIEW_PAGE_SIZE = 20;
const DETAIL_POLL_INTERVAL_MS = 1200;
const DETAIL_POLL_MAX_ATTEMPTS = 25;

interface DetailLoadOptions {
  force?: boolean;
  silent?: boolean;
}

export type GameDetailTabKey = 'intro' | 'stats' | 'reviews' | 'discuss';

const GAME_DETAIL_TABS: GameDetailTabKey[] = [
  'intro',
  'stats',
  'reviews',
  'discuss',
];

interface GameDetailCache {
  detail?: IGameDetail;
  ratingStats?: IGameRatingStats | null;
  followed?: boolean;
  reviews: Map<string, { items: IGameReview[]; total: number }>;
  myReviewLoaded: boolean;
  myReview: IGameReview | null;
  steamStatsLoaded: boolean;
  steamStats: ISteamGameStats | null;
}

function parseGameTab(value: string | null): GameDetailTabKey {
  if (value && GAME_DETAIL_TABS.includes(value as GameDetailTabKey)) {
    return value as GameDetailTabKey;
  }
  return 'intro';
}

export function useGameDetail(appId: number) {
  const [searchParams, setSearchParams] = useSearchParams();
  const activeTab = parseGameTab(searchParams.get('tab'));
  const discussionPageRef = useRef(1);
  const accountId = useAppSelector((state) => state.auth.user?.accountId);
  const gameCacheKey = `${appId}:${accountId ?? 'anonymous'}`;
  const cached = getPageDataCache<GameDetailCache>(gameCacheKey);

  const setActiveTab = useCallback(
    (tab: GameDetailTabKey) => {
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          if (tab === 'intro') {
            next.delete('tab');
          } else {
            next.set('tab', tab);
          }
          return next;
        },
        { replace: true },
      );
    },
    [setSearchParams],
  );

  const [detail, setDetail] = useState<IGameDetail | null>(
    cached?.detail ?? null,
  );
  const [ratingStats, setRatingStats] = useState<IGameRatingStats | null>(
    cached?.ratingStats ?? null,
  );
  const [reviews, setReviews] = useState<IGameReview[]>(
    cached?.reviews.get('latest:1')?.items ?? [],
  );
  const [reviewsLoading, setReviewsLoading] = useState(false);
  const [reviewsPage, setReviewsPage] = useState(1);
  const [reviewSort, setReviewSort] = useState<GameReviewSort>('latest');
  const [reviewsTotal, setReviewsTotal] = useState(
    cached?.reviews.get('latest:1')?.total ?? 0,
  );
  const [myReview, setMyReview] = useState<IGameReview | null>(
    cached?.myReviewLoaded ? cached.myReview : null,
  );
  const [myReviewLoading, setMyReviewLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(!cached?.detail);
  const [detailRefreshing, setDetailRefreshing] = useState(
    Boolean(cached?.detail && !isGameDetailReady(cached.detail)),
  );
  const [detailError, setDetailError] = useState<string | null>(null);
  const [reviewSubmitting, setReviewSubmitting] = useState(false);
  const [followed, setFollowed] = useState(cached?.followed ?? false);
  const [followLoading, setFollowLoading] = useState(false);
  const [steamStats, setSteamStats] = useState<ISteamGameStats | null>(
    cached?.steamStatsLoaded ? cached.steamStats : null,
  );
  const [steamStatsLoading, setSteamStatsLoading] = useState(false);
  const [steamAchievementSyncing, setSteamAchievementSyncing] = useState(false);
  const achievementAutoSyncRef = useRef(false);
  const achievementPollRef = useRef<ReturnType<typeof setInterval> | null>(
    null,
  );
  const detailPollRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const fetchBatch = useCallback(
    async (cursor: string | undefined, size: number) => {
      const page = cursor ? Number(cursor) + 1 : 1;
      const res = await fetchGameDiscussionsApi(appId, { page, size });
      discussionPageRef.current = page;
      return res.data;
    },
    [appId],
  );

  const discussions = useCursorList({
    pageSize: DISCUSSION_PAGE_SIZE,
    cacheKey: `game-discussions:${appId}`,
    enabled: Number.isFinite(appId) && appId > 0 && activeTab === 'discuss',
    resetDeps: [appId, activeTab],
    getCursor: () => String(discussionPageRef.current),
    fetchBatch,
  });

  const handleDiscussionLike = useFeedItemLike(discussions.setItems);

  const loadDetail = useCallback(
    async (options: DetailLoadOptions = {}): Promise<boolean> => {
      const { force = false, silent = false } = options;
      if (!Number.isFinite(appId) || appId <= 0) {
        setDetailError('无效的游戏 ID');
        setDetailLoading(false);
        setDetailRefreshing(false);
        return true;
      }
      const cachedGame = getPageDataCache<GameDetailCache>(gameCacheKey);
      if (
        !force &&
        cachedGame?.detail &&
        isGameDetailReady(cachedGame.detail)
      ) {
        setDetail(cachedGame.detail);
        setRatingStats(cachedGame.ratingStats ?? null);
        setDetailError(null);
        setDetailLoading(false);
        setDetailRefreshing(false);
        return true;
      }
      if (!silent && !cachedGame?.detail) setDetailLoading(true);
      if (!silent) setDetailError(null);
      try {
        const res = await fetchGameDetailApi(appId);
        const game = res.data as GameDetailPayload;
        setDetail(game);
        setDetailError(null);
        const ready = isGameDetailReady(game);
        setDetailRefreshing(!ready);
        const nextRatingStats = toRatingStats(game);
        setRatingStats(nextRatingStats);
        const existing = getPageDataCache<GameDetailCache>(gameCacheKey) ?? {
          reviews: new Map(),
          followed: undefined,
          myReviewLoaded: false,
          myReview: null,
          steamStatsLoaded: false,
          steamStats: null,
        };
        setPageDataCache(gameCacheKey, {
          ...existing,
          detail: game,
          ratingStats: nextRatingStats,
        });
        return ready;
      } catch (err) {
        if (!silent) {
          if (!cachedGame?.detail) setDetail(null);
          setDetailError(formatApiError('加载游戏详情失败', err));
          setDetailRefreshing(false);
        }
        return isGameDetailReady(cachedGame?.detail);
      } finally {
        if (!silent) setDetailLoading(false);
      }
    },
    [appId, gameCacheKey],
  );

  const loadReviews = useCallback(
    async (page = 1) => {
      if (!Number.isFinite(appId) || appId <= 0) return;
      const existing = getPageDataCache<GameDetailCache>(gameCacheKey);
      const cacheKey = `${reviewSort}:${page}`;
      const cachedPage = existing?.reviews.get(cacheKey);
      if (cachedPage) {
        setReviews(cachedPage.items);
        setReviewsPage(page);
        setReviewsTotal(cachedPage.total);
        setReviewsLoading(false);
        return;
      }
      setReviewsLoading(true);
      try {
        const res = await fetchGameReviewsApi(
          appId,
          page,
          REVIEW_PAGE_SIZE,
          reviewSort,
        );
        setReviews(res.data || []);
        setReviewsPage(res.page || page);
        setReviewsTotal(res.total || 0);
        const nextCache = getPageDataCache<GameDetailCache>(gameCacheKey) ?? {
          reviews: new Map(),
          followed: undefined,
          myReviewLoaded: false,
          myReview: null,
          steamStatsLoaded: false,
          steamStats: null,
        };
        nextCache.reviews.set(cacheKey, {
          items: res.data || [],
          total: res.total || 0,
        });
        setPageDataCache(gameCacheKey, nextCache);
      } catch (err) {
        message.error(formatApiError('加载评价失败', err));
      } finally {
        setReviewsLoading(false);
      }
    },
    [appId, gameCacheKey, reviewSort],
  );

  const loadMyReview = useCallback(async () => {
    if (!Number.isFinite(appId) || appId <= 0) return;
    const existing = getPageDataCache<GameDetailCache>(gameCacheKey);
    if (existing?.myReviewLoaded) {
      setMyReview(existing.myReview);
      setMyReviewLoading(false);
      return;
    }
    setMyReviewLoading(true);
    try {
      const res = await fetchMyGameReviewApi(appId);
      setMyReview(res.data ?? null);
      const nextCache = getPageDataCache<GameDetailCache>(gameCacheKey) ?? {
        reviews: new Map(),
        followed: undefined,
        myReviewLoaded: false,
        myReview: null,
        steamStatsLoaded: false,
        steamStats: null,
      };
      nextCache.myReviewLoaded = true;
      nextCache.myReview = res.data ?? null;
      setPageDataCache(gameCacheKey, nextCache);
    } catch {
      setMyReview(null);
    } finally {
      setMyReviewLoading(false);
    }
  }, [appId, gameCacheKey]);

  const loadFollowStatus = useCallback(async () => {
    if (!Number.isFinite(appId) || appId <= 0) return;
    const existing = getPageDataCache<GameDetailCache>(gameCacheKey);
    if (existing?.followed !== undefined) {
      setFollowed(existing.followed);
      return;
    }
    try {
      const res = await checkGameFollowApi(appId);
      setFollowed(Boolean(res.data));
      const nextCache = getPageDataCache<GameDetailCache>(gameCacheKey) ?? {
        reviews: new Map(),
        followed: undefined,
        myReviewLoaded: false,
        myReview: null,
        steamStatsLoaded: false,
        steamStats: null,
      };
      nextCache.followed = Boolean(res.data);
      setPageDataCache(gameCacheKey, nextCache);
    } catch {
      setFollowed(false);
    }
  }, [appId, gameCacheKey]);

  const loadSteamStats = useCallback(async () => {
    if (!Number.isFinite(appId) || appId <= 0) return;
    const existing = getPageDataCache<GameDetailCache>(gameCacheKey);
    if (existing?.steamStatsLoaded) {
      setSteamStats(existing.steamStats);
      setSteamStatsLoading(false);
      return;
    }
    setSteamStatsLoading(true);
    try {
      const res = await fetchSteamGameStatsApi(appId);
      setSteamStats(res.data ?? null);
      const nextCache = getPageDataCache<GameDetailCache>(gameCacheKey) ?? {
        reviews: new Map(),
        followed: undefined,
        myReviewLoaded: false,
        myReview: null,
        steamStatsLoaded: false,
        steamStats: null,
      };
      nextCache.steamStatsLoaded = true;
      nextCache.steamStats = res.data ?? null;
      setPageDataCache(gameCacheKey, nextCache);
    } catch {
      setSteamStats(null);
    } finally {
      setSteamStatsLoading(false);
    }
  }, [appId, gameCacheKey]);

  const pollSteamAchievementStats = useCallback(async () => {
    if (!Number.isFinite(appId) || appId <= 0) return;
    if (achievementPollRef.current) clearInterval(achievementPollRef.current);
    let attempts = 0;
    achievementPollRef.current = setInterval(() => {
      attempts += 1;
      void fetchSteamGameStatsApi(appId)
        .then((res) => {
          const nextStats = res.data ?? null;
          setSteamStats(nextStats);
          const cachedGame = getPageDataCache<GameDetailCache>(gameCacheKey);
          if (cachedGame) {
            cachedGame.steamStatsLoaded = true;
            cachedGame.steamStats = nextStats;
          }
          const finished = Boolean(
            nextStats &&
            ((nextStats.achievements?.length ?? 0) > 0 ||
              nextStats.achievementStatus === 'FAILED' ||
              nextStats.achievementStatus === 'NOT_AVAILABLE'),
          );
          if (finished || attempts >= 12) {
            if (achievementPollRef.current)
              clearInterval(achievementPollRef.current);
            achievementPollRef.current = null;
            setSteamAchievementSyncing(false);
          }
        })
        .catch(() => {
          if (attempts >= 12) {
            if (achievementPollRef.current)
              clearInterval(achievementPollRef.current);
            achievementPollRef.current = null;
            setSteamAchievementSyncing(false);
          }
        });
    }, 1500);
  }, [appId, gameCacheKey]);

  const maybeSyncSteamAchievements = useCallback(
    async (stats: ISteamGameStats | null) => {
      if (
        !stats?.owned ||
        !stats.achievementTotal ||
        (stats.achievements?.length ?? 0) > 0 ||
        achievementAutoSyncRef.current
      ) {
        return;
      }
      if (stats.achievementStatus === 'NOT_AVAILABLE') return;
      achievementAutoSyncRef.current = true;
      setSteamAchievementSyncing(true);
      try {
        const res = await syncSteamGameAchievementsApi(appId);
        setSteamStats(res.data ?? stats);
        await pollSteamAchievementStats();
      } catch {
        setSteamAchievementSyncing(false);
      }
    },
    [appId, pollSteamAchievementStats],
  );

  const syncSteamAchievements = useCallback(async () => {
    if (!Number.isFinite(appId) || appId <= 0) return;
    setSteamAchievementSyncing(true);
    try {
      const res = await syncSteamGameAchievementsApi(appId);
      setSteamStats(res.data ?? null);
      const cachedGame = getPageDataCache<GameDetailCache>(gameCacheKey);
      if (cachedGame) {
        cachedGame.steamStatsLoaded = true;
        cachedGame.steamStats = res.data ?? null;
      }
      message.success('成就同步任务已提交，完成后会自动刷新');
      await pollSteamAchievementStats();
    } catch (err) {
      message.error(formatApiError('提交成就同步失败', err));
      setSteamAchievementSyncing(false);
    }
  }, [appId, gameCacheKey, pollSteamAchievementStats]);

  const toggleFollow = useCallback(async () => {
    if (!Number.isFinite(appId) || appId <= 0) return;
    setFollowLoading(true);
    try {
      if (followed) {
        await unfollowGameApi(appId);
        setFollowed(false);
        const cachedGame = getPageDataCache<GameDetailCache>(gameCacheKey);
        if (cachedGame) cachedGame.followed = false;
        message.success('已移出我的游戏');
      } else {
        await followGameApi(appId, 'discover');
        setFollowed(true);
        const cachedGame = getPageDataCache<GameDetailCache>(gameCacheKey);
        if (cachedGame) cachedGame.followed = true;
        message.success('已加入我的游戏');
      }
    } catch (err) {
      message.error(formatApiError('操作失败', err));
    } finally {
      setFollowLoading(false);
    }
  }, [appId, followed, gameCacheKey]);

  const loadStatsAndSyncAchievements = useCallback(async () => {
    await loadSteamStats();
    const current =
      getPageDataCache<GameDetailCache>(gameCacheKey)?.steamStats ?? null;
    void maybeSyncSteamAchievements(current);
  }, [gameCacheKey, loadSteamStats, maybeSyncSteamAchievements]);

  useEffect(() => {
    let disposed = false;

    const schedulePoll = (attempt: number) => {
      if (disposed) return;
      if (attempt >= DETAIL_POLL_MAX_ATTEMPTS) {
        setDetailRefreshing(false);
        return;
      }
      detailPollRef.current = setTimeout(() => {
        void loadDetail({ force: true, silent: true }).then((ready) => {
          if (disposed || ready) return;
          schedulePoll(attempt + 1);
        });
      }, DETAIL_POLL_INTERVAL_MS);
    };

    const hasCachedDetail = Boolean(
      getPageDataCache<GameDetailCache>(gameCacheKey)?.detail,
    );
    void loadDetail({ force: true, silent: hasCachedDetail }).then((ready) => {
      if (!disposed && !ready) schedulePoll(0);
    });
    void loadFollowStatus();
    return () => {
      disposed = true;
      if (detailPollRef.current) clearTimeout(detailPollRef.current);
      detailPollRef.current = null;
    };
  }, [gameCacheKey, loadDetail, loadFollowStatus]);

  useEffect(() => {
    if (activeTab === 'reviews') {
      void loadReviews(1);
      void loadMyReview();
    }
    if (activeTab === 'stats') {
      void loadStatsAndSyncAchievements();
    }
  }, [activeTab, loadReviews, loadMyReview, loadStatsAndSyncAchievements]);

  useEffect(
    () => () => {
      if (achievementPollRef.current) clearInterval(achievementPollRef.current);
    },
    [],
  );

  const saveMyReview = useCallback(
    async (score: number, content: string) => {
      if (!Number.isFinite(appId) || appId <= 0) return;
      setReviewSubmitting(true);
      try {
        await saveMyGameReviewApi(appId, {
          score,
          content: content.trim() || undefined,
        });
        message.success('评价已保存');
        const cachedGame = getPageDataCache<GameDetailCache>(gameCacheKey);
        if (cachedGame) {
          cachedGame.detail = undefined;
          cachedGame.myReviewLoaded = false;
          cachedGame.reviews.delete('latest:1');
          cachedGame.reviews.delete('hot:1');
        }
        await loadMyReview();
        await loadReviews(1);
        await loadDetail();
      } catch (err) {
        message.error(formatApiError('保存评价失败', err));
      } finally {
        setReviewSubmitting(false);
      }
    },
    [appId, gameCacheKey, loadDetail, loadMyReview, loadReviews],
  );

  const removeMyReview = useCallback(async () => {
    if (!Number.isFinite(appId) || appId <= 0) return;
    setReviewSubmitting(true);
    try {
      await deleteMyGameReviewApi(appId);
      setMyReview(null);
      message.success('已删除评价');
      const cachedGame = getPageDataCache<GameDetailCache>(gameCacheKey);
      if (cachedGame) {
        cachedGame.detail = undefined;
        cachedGame.myReviewLoaded = false;
        cachedGame.myReview = null;
        cachedGame.reviews.delete('latest:1');
        cachedGame.reviews.delete('hot:1');
      }
      await loadReviews(1);
      await loadDetail();
    } catch (err) {
      message.error(formatApiError('删除评价失败', err));
    } finally {
      setReviewSubmitting(false);
    }
  }, [appId, gameCacheKey, loadDetail, loadReviews]);

  return {
    activeTab,
    setActiveTab,
    detail,
    ratingStats,
    reviews,
    reviewsLoading,
    reviewsPage,
    reviewsTotal,
    reviewSort,
    setReviewSort,
    reviewPageSize: REVIEW_PAGE_SIZE,
    loadReviews,
    myReview,
    myReviewLoading,
    detailLoading,
    detailRefreshing,
    detailError,
    reviewSubmitting,
    saveMyReview,
    removeMyReview,
    followed,
    followLoading,
    toggleFollow,
    steamStats,
    steamStatsLoading,
    loadSteamStats,
    steamAchievementSyncing,
    syncSteamAchievements,
    discussions: {
      items: discussions.items,
      loading: discussions.loading,
      loadingMore: discussions.loadingMore,
      hasMore: discussions.hasMore,
      sentinelRef: discussions.sentinelRef,
      reload: discussions.reload,
      handleLike: handleDiscussionLike,
    },
  };
}
