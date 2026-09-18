import hyRequest from '@/service/request';
import type { IDataType } from '@/service/types';
import { mapArticlesToLatestPosts } from '@/service/social';
import { fetchArticlesRawByIds } from '@/utils/hydrateArticleForGameRepost';
import type { IArticleRaw } from '@/utils/mapPost';
import type { IGameTagRaw } from '@/utils/mapGameTag';
import type { LatestPostItem } from '@/types/post';

export type HotRankBoard = 'total' | 'weekly' | 'daily';

export interface IHotRankItem {
  /** 后端公开接口不再返回内部数据库 id；仅作为旧服务兼容兜底。 */
  id?: number;
  publicId: string;
  rank?: number;
  authorAccountId: number;
  title: string;
  summary?: string;
  coverUrl?: string;
  videoUrl?: string;
  postType?: number;
  refArticleId?: string;
  categoryId?: number;
  categoryIds?: number[];
  categoryName?: string;
  categoryNames?: string[];
  gameTags?: IGameTagRaw[];
  boardType?: string;
  periodKey?: string;
  authorName?: string;
  authorAvatar?: string;
  likeCount?: number;
  commentCount?: number;
  replyCount?: number;
  viewCount?: number;
  liked?: boolean;
  hotScore?: number;
  publishedTime?: string;
  createTime?: string;
  updateTime?: string;
}

export interface IFetchHotRankParams {
  board: HotRankBoard;
  categoryId?: number;
  periodKey?: string;
  refresh?: boolean;
}

export async function fetchHotRankApi(params: IFetchHotRankParams) {
  const res = await hyRequest.get<IDataType<IHotRankItem[]>>({
    url: '/hot-article/rank',
    params: {
      board: params.board,
      categoryId: params.categoryId,
      periodKey: params.periodKey,
      refresh: params.refresh,
    },
  });
  return res.data || [];
}

/** 热榜条目走与首页/动态一致的文章映射（含游戏分享封面） */
function hotRankItemToArticleRaw(item: IHotRankItem): IArticleRaw {
  return {
    id: item.id ?? 0,
    publicId: item.publicId,
    authorAccountId: item.authorAccountId,
    username: item.authorName,
    avatar: item.authorAvatar,
    title: item.title,
    summary: item.summary,
    coverUrl: item.coverUrl,
    videoUrl: item.videoUrl,
    postType: item.postType,
    refArticleId: item.refArticleId,
    categoryId: item.categoryId,
    categoryIds: item.categoryIds,
    categoryNames:
      item.categoryNames ??
      (item.categoryName ? [item.categoryName] : undefined),
    gameTags: item.gameTags,
    publishedTime: item.publishedTime,
    createTime: item.createTime,
    updateTime: item.updateTime,
  };
}

export async function mapHotRankItemsToLatest(
  items: IHotRankItem[],
): Promise<LatestPostItem[]> {
  if (items.length === 0) return [];

  const publicIds = items.map((item) => item.publicId);
  const fetched = await fetchArticlesRawByIds(publicIds);
  const fetchedById = new Map(fetched.map((row) => [row.publicId, row]));
  const articles = items.map((item) => {
    const fallback = hotRankItemToArticleRaw(item);
    const canonical = fetchedById.get(item.publicId);
    if (!canonical) return fallback;
    // 热榜 VO 是精简结构，内容服务补全时也可能暂时没有游戏标签；
    // 保留热榜自身或精简接口携带的字段，避免标签在二次映射中丢失。
    return {
      ...fallback,
      ...canonical,
      username: canonical.username ?? fallback.username,
      avatar: canonical.avatar ?? fallback.avatar,
      gameTags: canonical.gameTags ?? fallback.gameTags,
    };
  });
  const mapped = await mapArticlesToLatestPosts(articles);
  const mappedById = new Map(mapped.map((item) => [item.id, item]));

  const result: LatestPostItem[] = [];
  for (const item of items) {
    const base = mappedById.get(item.publicId);
    if (!base) continue;
    result.push({
      ...base,
      liked: item.liked ?? base.liked,
      rank: item.rank,
      hotScore:
        item.hotScore != null && Number.isFinite(Number(item.hotScore))
          ? Number(item.hotScore)
          : base.hotScore,
    });
  }
  return result;
}
