import { getArticleDetailApi, listArticlesByIdsApi } from '@/service/content';
import type { IArticleRaw } from '@/utils/mapPost';
import {
  isGameRepostArticle,
  resolveGameRepostAppId,
} from '@/utils/gameRepost';

function detailToArticleRaw(
  detail: NonNullable<Awaited<ReturnType<typeof getArticleDetailApi>>['data']>,
): IArticleRaw {
  return {
    id: detail.id,
    publicId: detail.publicId,
    authorAccountId: detail.authorAccountId,
    title: detail.title,
    summary: detail.summary,
    coverUrl: detail.coverUrl,
    videoUrl: detail.videoUrl,
    postType: detail.postType,
    refArticleId: detail.refArticleId,
    categoryId: detail.categoryId,
    categoryIds: detail.categoryIds,
    gameTags: detail.gameTags,
    content: detail.content,
    contentParagraphs: detail.contentParagraphs,
    status: detail.status,
    publishedTime: detail.publishedTime,
    createTime: detail.createTime,
    updateTime: detail.updateTime,
  };
}

function mergeHydratedArticle(
  original: IArticleRaw,
  hydrated: IArticleRaw,
): IArticleRaw {
  return {
    ...hydrated,
    gameTags: hydrated.gameTags?.length ? hydrated.gameTags : original.gameTags,
    content: hydrated.content || original.content,
    summary: hydrated.summary || original.summary,
    contentParagraphs:
      hydrated.contentParagraphs &&
      Object.keys(hydrated.contentParagraphs).length > 0
        ? hydrated.contentParagraphs
        : original.contentParagraphs,
  };
}

function needsGameRepostHydration(article: IArticleRaw): boolean {
  if (!isGameRepostArticle(article)) return false;
  if (!resolveGameRepostAppId(article)) return true;
  return !article.gameTags?.length;
}

/** 列表接口字段不全时，按 id 补拉详情以还原游戏分享封面/标签 */
export async function hydrateArticlesForGameRepost(
  articles: IArticleRaw[],
): Promise<IArticleRaw[]> {
  const hydrateIds = articles
    .filter(needsGameRepostHydration)
    .map((article) => article.publicId);
  if (hydrateIds.length === 0) return articles;

  const uniqueIds = Array.from(new Set(hydrateIds));
  const detailMap = new Map<string, IArticleRaw>();

  await Promise.all(
    uniqueIds.map(async (id) => {
      try {
        const res = await getArticleDetailApi(id);
        if (res.data) detailMap.set(id, detailToArticleRaw(res.data));
      } catch {
        /* 单条失败不影响列表 */
      }
    }),
  );

  return articles.map((article) => {
    const hydrated = detailMap.get(article.publicId);
    if (!hydrated) return article;
    return mergeHydratedArticle(article, hydrated);
  });
}

/** 按文章 id 批量拉列表（含 gameTags；热榜等精简接口优先走此路径） */
export async function fetchArticlesRawByIds(
  ids: string[],
): Promise<IArticleRaw[]> {
  const uniqueIds = Array.from(new Set(ids.filter(Boolean)));
  if (uniqueIds.length === 0) return [];

  try {
    const res = await listArticlesByIdsApi(uniqueIds);
    const list = (res.data || []) as IArticleRaw[];
    if (list.length > 0) {
      const map = new Map(list.map((row) => [row.publicId, row]));
      return uniqueIds
        .map((id) => map.get(id))
        .filter((row): row is IArticleRaw => row != null);
    }
  } catch {
    /* 回退逐条详情 */
  }

  const rows = await Promise.all(
    uniqueIds.map(async (id) => {
      try {
        const res = await getArticleDetailApi(id);
        return res.data ? detailToArticleRaw(res.data) : null;
      } catch {
        return null;
      }
    }),
  );

  const map = new Map<string, IArticleRaw>();
  rows.forEach((row) => {
    if (row) map.set(row.publicId, row);
  });

  return uniqueIds
    .map((id) => map.get(id))
    .filter((row): row is IArticleRaw => row != null);
}
