import hyRequest from '@/service/request';
import type { IDataType } from '@/service/types';
import {
  authorFrom,
  mapArticleToLatest,
  type IArticleRaw,
  type IArticleStatsRaw,
} from '@/utils/mapPost';
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

/** 将热榜接口已经返回的首屏字段直接映射为卡片模型，避免重复请求文章详情和统计。 */
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
    // 热榜只返回已发布文章；补齐状态可让卡片映射保持和普通文章一致。
    status: 1,
    publishedTime: item.publishedTime,
    createTime: item.createTime,
    updateTime: item.updateTime,
  };
}

export async function mapHotRankItemsToLatest(
  items: IHotRankItem[],
): Promise<LatestPostItem[]> {
  if (items.length === 0) return [];

  return items.map((item) => {
    const raw = hotRankItemToArticleRaw(item);
    const stats: IArticleStatsRaw = {
      articleId: item.id ?? item.publicId,
      publicId: item.publicId,
      likeCount: item.likeCount,
      commentCount: item.commentCount,
      replyCount: item.replyCount,
      viewCount: item.viewCount,
      liked: item.liked,
    };
    const base = mapArticleToLatest(
      raw,
      authorFrom(item.authorAccountId, item.authorName, item.authorAvatar),
      stats,
    );
    return {
      ...base,
      rank: item.rank,
      hotScore:
        item.hotScore != null && Number.isFinite(Number(item.hotScore))
          ? Number(item.hotScore)
          : base.hotScore,
    };
  });
}
