import { resolveRefPostMap } from '@/service/social';
import type { PostRefCard } from '@/types/post';
import { fetchGameRepostMetaMap } from '@/utils/fetchGameRepostMeta';
import {
  buildGameRepostRefPostForArticle,
  isGameRepostArticle,
  resolveDisplayPostType,
  resolveGameRepostAppId,
  sanitizeGameShareContent,
} from '@/utils/gameRepost';
import { hydrateArticlesForGameRepost } from '@/utils/hydrateArticleForGameRepost';
import { mapGameTagsFromRaw } from '@/utils/mapGameTag';
import type { IArticleRaw } from '@/utils/mapPost';
import type { PostCoverSource } from '@/utils/postCover';
import { resolveRepostRefPost } from '@/utils/refPostCard';

function buildPostCoverSourceFromArticle(
  raw: IArticleRaw,
  refPostMap: Map<string, PostRefCard>,
  gameRepostMetaMap: Record<number, { summary?: string; coverUrl?: string }>,
): PostCoverSource {
  const postType = resolveDisplayPostType(raw);
  const appId = resolveGameRepostAppId(raw);
  const gameRepostRef = isGameRepostArticle(raw)
    ? buildGameRepostRefPostForArticle(
        raw,
        appId ? gameRepostMetaMap[appId] : undefined,
      )
    : undefined;
  const refPost = gameRepostRef
    ? gameRepostRef
    : raw.refArticleId
      ? resolveRepostRefPost(raw.refArticleId, refPostMap.get(raw.refArticleId))
      : undefined;

  const cover = raw.coverUrl?.trim() || undefined;
  const summary = raw.summary?.trim() || '';
  const content =
    postType === 'repost' && isGameRepostArticle(raw)
      ? sanitizeGameShareContent(raw)
      : raw.content?.trim() || summary;

  return {
    id: raw.publicId,
    title: raw.title,
    summary,
    content,
    postType,
    coverUrl: postType === 'video' ? cover : undefined,
    videoUrl: raw.videoUrl?.trim() || undefined,
    images: cover ? [cover] : undefined,
    refPost,
    gameTags: mapGameTagsFromRaw(raw.gameTags),
  };
}

/** 与首页信息流同款：批量解析通知帖封面源 */
export async function buildNotificationCoverSources(
  articles: IArticleRaw[],
): Promise<Map<number, PostCoverSource>> {
  if (articles.length === 0) return new Map();

  const hydrated = await hydrateArticlesForGameRepost(articles);
  const refPostMap = await resolveRefPostMap(hydrated);
  const gameRepostAppIds = hydrated
    .filter((article) => isGameRepostArticle(article))
    .map((article) => resolveGameRepostAppId(article))
    .filter((appId): appId is number => appId != null && appId > 0);
  const gameRepostMetaMap = await fetchGameRepostMetaMap(gameRepostAppIds);

  const map = new Map<number, PostCoverSource>();
  hydrated.forEach((raw) => {
    map.set(
      raw.id,
      buildPostCoverSourceFromArticle(raw, refPostMap, gameRepostMetaMap),
    );
  });
  return map;
}
