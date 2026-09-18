import hyRequest from './request';
import {
  ARTICLE_STATUS,
  listCategoriesApi,
  POST_TYPE,
  saveArticleApi,
} from './content';
import { mapArticlesToLatestPosts } from '@/service/social';
import type { IDataType, IPageResult } from './types';
import type {
  GameChartBoard,
  GameDiscoverBoard,
  IGameChartItem,
  IGameDetail,
  IGameDiscoverQuery,
  IGameReview,
  IGameReviewReply,
  IGameReviewSavePayload,
} from '@/types/game';
import type { LatestPostItem } from '@/types/post';
import type { IArticleRaw } from '@/utils/mapPost';
import { appendGameShareMarker } from '@/utils/gameRepost';
import { mapGameListItem, type IGameListEnrichment } from '@/utils/mapGameItem';
import { resolveGameDisplayName } from '@/utils/gameDisplayName';
import { resolveSteamReviewMetrics } from '@/utils/mapSteamReview';
import { searchGameIndexApi } from './search';

interface IGameDetailRaw {
  appId: number;
  name: string;
  detailReady?: boolean;
  localizedName?: string;
  nameCn?: string;
  chineseName?: string;
  shortDescription?: string;
  aboutHtml?: string;
  headerImage?: string;
  developers?: string[];
  publishers?: string[];
  genres?: string[];
  categories?: string[];
  releaseDate?: string;
  steamUrl?: string;
  steamScore?: number;
  steamReviewScore?: number;
  steamReviewCount?: number;
  discussCount?: number;
  screenshots?: { thumbnailUrl?: string; fullUrl: string }[];
  movies?: {
    name?: string;
    thumbnailUrl?: string;
    mp4Url?: string;
    webmUrl?: string;
  }[];
  price?: {
    free?: boolean;
    currency?: string;
    initial?: number;
    finalPrice?: number;
    discountPercent?: number;
    discountEndAt?: string | number;
    formatted?: string;
  };
  metacritic?: { score?: number; url?: string };
  achievementTotal?: number;
  achievementHighlights?: { name: string; iconUrl?: string }[];
  pcRequirementsMin?: string;
  pcRequirementsRec?: string;
  rating?: {
    avgScore?: number;
    reviewCount?: number;
  };
}

function stripHtml(value?: string | null): string {
  if (!value?.trim()) return '';
  return value
    .replace(/<[^>]+>/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function mapGamePrice(
  raw?: IGameDetailRaw['price'],
): IGameDetail['price'] | undefined {
  if (!raw) return undefined;
  const discountEndAt =
    raw.discountEndAt ??
    (raw as { discountEndTime?: string | number }).discountEndTime;
  return {
    ...raw,
    discountEndAt,
  };
}

export function mapGameDetail(raw: IGameDetailRaw): IGameDetail & {
  averageScore?: number;
  reviewCount?: number;
} {
  const steamReview = resolveSteamReviewMetrics(raw);

  return {
    appId: raw.appId,
    name: resolveGameDisplayName(raw) || raw.name,
    detailReady: raw.detailReady,
    coverUrl: raw.headerImage,
    shortDescription: raw.shortDescription,
    aboutHtml: raw.aboutHtml,
    description: raw.shortDescription || stripHtml(raw.aboutHtml),
    tags: raw.genres,
    categories: raw.categories,
    releaseDate: raw.releaseDate,
    developer: raw.developers?.[0],
    publisher: raw.publishers?.[0],
    steamUrl: raw.steamUrl,
    steamReviewScore: steamReview.score,
    steamReviewCount: steamReview.count,
    discussCount: raw.discussCount,
    screenshots: raw.screenshots,
    movies: raw.movies,
    price: mapGamePrice(raw.price),
    metacritic: raw.metacritic,
    achievementTotal: raw.achievementTotal,
    achievementHighlights: raw.achievementHighlights,
    pcRequirementsMin: raw.pcRequirementsMin,
    pcRequirementsRec: raw.pcRequirementsRec,
    averageScore:
      raw.rating?.avgScore != null ? Number(raw.rating.avgScore) : undefined,
    reviewCount: raw.rating?.reviewCount,
  };
}

export async function fetchGameDetailApi(appId: number) {
  const res = await hyRequest.get<IDataType<IGameDetailRaw>>({
    url: `/game/${appId}`,
  });
  return {
    ...res,
    data: mapGameDetail(res.data),
  };
}

export async function fetchGameListEnrichmentApi(
  appIds: number[],
): Promise<Record<number, IGameListEnrichment>> {
  const unique = Array.from(new Set(appIds.filter((id) => id > 0)));
  if (unique.length === 0) return {};

  const entries = await Promise.all(
    unique.map(async (appId): Promise<[number, IGameListEnrichment] | null> => {
      try {
        const res = await hyRequest.get<IDataType<IGameDetailRaw>>({
          url: `/game/${appId}`,
        });
        const raw = res.data;
        const detail = mapGameDetail(raw);

        return [
          appId,
          {
            name: detail.name,
            genres: detail.tags,
            developer: detail.developer,
            publisher: detail.publisher,
            releaseDate: detail.releaseDate,
            steamScore: detail.steamReviewScore,
            avgScore: detail.averageScore,
            reviewCount: detail.reviewCount,
          },
        ];
      } catch {
        return null;
      }
    }),
  );

  const result: Record<number, IGameListEnrichment> = {};
  for (const entry of entries) {
    if (entry) {
      result[entry[0]] = entry[1];
    }
  }
  return result;
}

/** @deprecated 使用 fetchGameListEnrichmentApi */
export async function fetchGameDisplayNamesApi(
  appIds: number[],
): Promise<Record<number, string>> {
  const enrichment = await fetchGameListEnrichmentApi(appIds);
  return Object.fromEntries(
    Object.entries(enrichment)
      .filter(([, value]) => value.name)
      .map(([appId, value]) => [Number(appId), value.name!]),
  );
}

export function fetchGameDiscoverApi(query: IGameDiscoverQuery = {}) {
  return hyRequest
    .get<IPageResult<IGameChartItem>>({
      url: '/game/discover',
      params: {
        board: query.board ?? 'all',
        sort: query.sort,
        order: query.order ?? 'desc',
        page: query.page ?? 1,
        size: query.size ?? 18,
        minSteamScore: query.minSteamScore,
        maxSteamScore: query.maxSteamScore,
        minSteamReviews: query.minSteamReviews,
        maxSteamReviews: query.maxSteamReviews,
        minPrice: query.minPrice,
        maxPrice: query.maxPrice,
        minFinalPrice: query.minFinalPrice,
        maxFinalPrice: query.maxFinalPrice,
        minDiscount: query.minDiscount,
        discountOnly: query.discountOnly,
        freeOnly: query.freeOnly,
      },
    })
    .then((res) => ({
      ...res,
      data: (res.data || []).map((item) => mapGameListItem(item)),
    }));
}

/** @deprecated 使用 fetchGameDiscoverApi */
export function fetchGameChartApi(board: GameChartBoard = 'hot') {
  return fetchGameDiscoverApi({ board, sort: 'rank', order: 'asc', size: 50 });
}

/** @deprecated 使用 fetchGameDiscoverApi */
export function fetchGamePageApi(
  options: { sort?: GameDiscoverBoard; page?: number; size?: number } = {},
) {
  return fetchGameDiscoverApi({
    board: 'all',
    page: options.page,
    size: options.size,
  });
}

export function searchGamesApi(
  keyword: string,
  options: { page?: number; size?: number } = {},
) {
  return searchGameIndexApi(keyword, options);
}

export function fetchGameReviewsApi(
  appId: number,
  page = 1,
  size = 20,
  sort: 'latest' | 'hot' = 'latest',
) {
  return hyRequest.get<IPageResult<IGameReview>>({
    url: `/game/${appId}/reviews`,
    params: { page, size, sort },
  });
}

export function fetchGameReviewRepliesApi(
  reviewId: string,
  page = 1,
  size = 20,
) {
  return hyRequest.get<IPageResult<IGameReviewReply>>({
    url: `/social/game-reviews/${reviewId}/replies`,
    params: { page, size },
  });
}

export function addGameReviewReplyApi(
  reviewId: string,
  content: string,
  options: { replyToReplyId?: string } = {},
) {
  return hyRequest.post<IDataType<string>>({
    url: `/social/game-reviews/${reviewId}/replies`,
    data: { content, replyToReplyId: options.replyToReplyId },
  });
}

export function deleteGameReviewReplyApi(replyId: string) {
  return hyRequest.delete<IDataType<null>>({
    url: `/social/game-reviews/replies/${replyId}`,
  });
}

export function likeGameReviewApi(reviewId: string) {
  return hyRequest.post<IDataType<null>>({
    url: `/social/game-reviews/${reviewId}/like`,
  });
}

export function unlikeGameReviewApi(reviewId: string) {
  return hyRequest.delete<IDataType<null>>({
    url: `/social/game-reviews/${reviewId}/like`,
  });
}

export function likeGameReviewReplyApi(replyId: string) {
  return hyRequest.post<IDataType<null>>({
    url: `/social/game-reviews/replies/${replyId}/like`,
  });
}

export function unlikeGameReviewReplyApi(replyId: string) {
  return hyRequest.delete<IDataType<null>>({
    url: `/social/game-reviews/replies/${replyId}/like`,
  });
}

export function fetchMyGameReviewApi(appId: number) {
  return hyRequest.get<IDataType<IGameReview | null>>({
    url: `/game/${appId}/reviews/mine`,
  });
}

export function saveMyGameReviewApi(
  appId: number,
  payload: IGameReviewSavePayload,
) {
  return hyRequest.put<IDataType<null>>({
    url: `/game/${appId}/reviews/mine`,
    data: payload,
  });
}

export function deleteMyGameReviewApi(appId: number) {
  return hyRequest.delete<IDataType<null>>({
    url: `/game/${appId}/reviews/mine`,
  });
}

/** 分享游戏为站内转发动态（后端 REPOST 需 refArticleId，故存为图文帖 + 游戏标签） */
export async function createGameSharePostApi(payload: {
  appId: number;
  gameName: string;
  gameSummary?: string;
  coverUrl?: string;
  title: string;
  content: string;
}): Promise<string> {
  const cats = await listCategoriesApi(1, 20);
  const categoryId = cats.data?.[0]?.id;
  if (!categoryId) throw new Error('缺少分区，无法分享');

  const markedContent = appendGameShareMarker(payload.content, payload.appId);
  const saveRes = await saveArticleApi({
    title: payload.title.slice(0, 80),
    summary: markedContent.slice(0, 200),
    content: markedContent,
    coverUrl: payload.coverUrl || null,
    categoryId,
    postType: POST_TYPE.IMAGE_TEXT,
    gameAppIds: [payload.appId],
    status: ARTICLE_STATUS.PENDING,
  });
  return saveRes.data;
}

/** 游戏讨论区帖子（页码分页，与后端 PageResult 对齐） */
export async function fetchGameDiscussionsApi(
  appId: number,
  options: { page?: number; size?: number } = {},
): Promise<IPageResult<LatestPostItem>> {
  const size = options.size ?? 20;
  const page = options.page ?? 1;
  const res = await hyRequest.get<IPageResult<IArticleRaw>>({
    url: `/article/by-game/${appId}`,
    params: { page, size },
  });
  const articles = res.data || [];
  return {
    code: res.code,
    message: res.message,
    data: await mapArticlesToLatestPosts(articles),
    page: Number(res.page ?? page),
    size: Number(res.size ?? size),
    total: Number(res.total ?? articles.length),
    expanding: Boolean(res.expanding),
  };
}
