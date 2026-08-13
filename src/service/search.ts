import hyRequest from './request';
import { listArticlesByIdsApi } from './content';
import type { IDataType, IPageResult } from './types';
import type { IGameListItem } from '@/types/game';
import { mapGameListItem } from '@/utils/mapGameItem';
import { mapArticlesToLatestPosts } from '@/service/social';
import type { LatestPostItem } from '@/types/post';
import type { IArticleRaw } from '@/utils/mapPost';
import type { IGameTagRaw } from '@/utils/mapGameTag';

export interface ISearchArticleItem {
  id: number;
  publicId: string;
  authorAccountId: number;
  username?: string;
  avatar?: string;
  title: string;
  summary?: string;
  coverUrl?: string;
  videoUrl?: string;
  postType?: number;
  refArticleId?: string;
  refArticle?: IArticleRaw['refArticle'];
  categoryId?: number;
  categoryName?: string;
  categoryIds?: number[];
  categoryNames?: string[];
  gameTags?: IGameTagRaw[];
  publishedTime?: string;
  createTime?: string;
  updateTime?: string;
}

type ISearchGameItem = IGameListItem & {
  steamReviewScore?: number;
  steamReviewCount?: number;
};

export interface ISuggestItem {
  id: number;
  term: string;
  weight?: number;
  sourceType?: string;
}

export interface ISearchHistoryItem {
  id: number;
  keyword: string;
  updateTime?: string;
}

export function fetchSuggestApi(prefix: string, sourceType?: string) {
  return hyRequest.get<IDataType<ISuggestItem[]>>({
    url: '/search/suggest',
    params: { prefix, sourceType },
  });
}

export function triggerSuggestApi(payload: { termId?: number; term: string }) {
  return hyRequest.post<IDataType<null>>({
    url: '/search/suggest/trigger',
    data: payload,
  });
}

export function fetchSearchHistoryApi() {
  return hyRequest.get<IDataType<ISearchHistoryItem[]>>({
    url: '/search/record/list',
  });
}

export function addSearchHistoryApi(keyword: string) {
  return hyRequest.post<IDataType<null>>({
    url: '/search/record',
    params: { keyword },
  });
}

export function deleteSearchHistoryApi(id: number) {
  return hyRequest.delete<IDataType<null>>({
    url: `/search/record/${id}`,
  });
}

export async function searchGameIndexApi(
  keyword: string,
  options: { page?: number; size?: number } = {},
) {
  const response = await hyRequest.get<IPageResult<ISearchGameItem>>({
    url: '/search/game',
    params: {
      q: keyword,
      page: options.page ?? 1,
      size: options.size ?? 20,
    },
  });
  return {
    ...response,
    data: (response.data || []).map(mapGameListItem),
  };
}

export function searchArticlesApi(params: {
  keyword: string;
  page?: number;
  size?: number;
}) {
  return hyRequest.get<IPageResult<ISearchArticleItem>>({
    url: '/search/article',
    params: {
      keyword: params.keyword,
      page: params.page ?? 1,
      size: params.size ?? 10,
    },
  });
}

/** 将 ES 搜索命中的完整帖子文档交给首页同一套卡片 mapper。 */
export async function mapSearchArticlesToFeedItems(
  items: ISearchArticleItem[],
): Promise<LatestPostItem[]> {
  const searchArticles: IArticleRaw[] = items.map((item) => ({
    id: item.id,
    publicId: item.publicId,
    authorAccountId: item.authorAccountId,
    username: item.username,
    avatar: item.avatar,
    title: item.title,
    summary: item.summary,
    coverUrl: item.coverUrl,
    videoUrl: item.videoUrl,
    postType: item.postType,
    refArticleId: item.refArticleId,
    refArticle: item.refArticle,
    categoryId: item.categoryId,
    categoryIds: item.categoryIds,
    categoryNames:
      item.categoryNames ||
      (item.categoryName ? [item.categoryName] : undefined),
    gameTags: item.gameTags,
    publishedTime: item.publishedTime,
    createTime: item.createTime,
    updateTime: item.updateTime,
  }));

  // ES 负责召回和排序；作者、分类、游戏标签以内容服务为准，避免索引延迟导致卡片展示旧数据。
  const publicIds = searchArticles.map((article) => article.publicId);
  try {
    const canonicalResponse = await listArticlesByIdsApi(publicIds);
    const canonicalMap = new Map(
      (canonicalResponse.data || []).map((article) => [
        article.publicId,
        article,
      ]),
    );
    const articles = searchArticles.map((article) => {
      const canonical = canonicalMap.get(article.publicId) as
        | ((typeof canonicalResponse.data)[number] & {
            categoryNames?: string[];
            gameTags?: IGameTagRaw[];
          })
        | undefined;
      if (!canonical) return article;

      return {
        ...article,
        id: canonical.id ?? article.id,
        publicId: canonical.publicId ?? article.publicId,
        authorAccountId: canonical.authorAccountId ?? article.authorAccountId,
        title: canonical.title || article.title,
        summary: canonical.summary ?? article.summary,
        coverUrl: canonical.coverUrl ?? article.coverUrl,
        videoUrl: canonical.videoUrl ?? article.videoUrl,
        postType: canonical.postType ?? article.postType,
        refArticleId: canonical.refArticleId ?? article.refArticleId,
        categoryId: canonical.categoryId ?? article.categoryId,
        categoryIds: canonical.categoryIds ?? article.categoryIds,
        categoryNames: canonical.categoryNames ?? article.categoryNames,
        gameTags: canonical.gameTags ?? article.gameTags,
        publishedTime: canonical.publishedTime ?? article.publishedTime,
        createTime: canonical.createTime ?? article.createTime,
        updateTime: canonical.updateTime ?? article.updateTime,
      };
    });
    return mapArticlesToLatestPosts(articles);
  } catch {
    // 内容服务暂不可用时，仍使用 ES 结果展示，保证搜索可用。
    return mapArticlesToLatestPosts(searchArticles);
  }
}
