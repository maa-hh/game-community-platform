import type { ContentCardPostType, PostRefCard } from '@/types/content';
import type { IGameTag } from '@/types/game';
import {
  isGameRepostArticle,
  resolveGameRepostAppId,
} from '@/utils/gameRepost';
import { resolvePostType } from '@/utils/postType';
import { resolveGameCoverUrl } from '@/utils/steamImage';

/** 封面解析入参（首页瀑布流 / 个人页行卡等共用） */
export interface PostCoverSource {
  id?: string;
  title?: string;
  summary?: string;
  content?: string;
  postType?: ContentCardPostType;
  mediaType?: 'image' | 'video';
  coverUrl?: string;
  videoUrl?: string;
  images?: string[];
  refPost?: PostRefCard;
  gameTags?: IGameTag[];
}

export interface IPostCoverMedia {
  isVideo: boolean;
  coverUrl?: string;
  videoUrl?: string;
  posterTitle: string;
}

/** 是否有真实封面媒体（不含标题生成海报） */
export function hasRealCoverMedia(params: {
  isVideo: boolean;
  coverUrl?: string;
  videoUrl?: string;
  coverError?: boolean;
}): boolean {
  if (params.isVideo) return Boolean(params.videoUrl);
  return Boolean(params.coverUrl?.trim()) && !params.coverError;
}

/** 瀑布流 / 行卡封面图 */
export function resolvePostCoverUrl(item: PostCoverSource): string | undefined {
  return resolvePostCoverMedia(item).coverUrl;
}

function toGameRepostDetectInput(item: PostCoverSource) {
  return {
    title: item.title,
    summary: item.summary,
    content: item.content,
    gameTags: item.gameTags?.map((tag) => ({
      appId: tag.appId,
      name: tag.name,
      iconUrl: tag.iconUrl,
    })),
  };
}

function isMappedGameShareItem(item: PostCoverSource): boolean {
  return (
    isGameRepostArticle(toGameRepostDetectInput(item)) ||
    (item.postType === 'repost' &&
      item.refPost?.id?.startsWith('game-') === true)
  );
}

function resolveGameShareCoverFallback(
  item: PostCoverSource,
): string | undefined {
  if (!isMappedGameShareItem(item)) return undefined;

  const detectInput = toGameRepostDetectInput(item);
  const appId =
    resolveGameRepostAppId(detectInput) ?? item.gameTags?.[0]?.appId;

  if (!appId) return undefined;

  const tag =
    item.gameTags?.find((entry) => entry.appId === appId) ?? item.gameTags?.[0];

  return resolveGameCoverUrl(appId, item.refPost?.coverUrl, tag?.iconUrl);
}

/** 瀑布流 / 行卡封面媒体（转发取被转发原帖） */
export function resolvePostCoverMedia(item: PostCoverSource): IPostCoverMedia {
  const postType = resolvePostType(item);

  if (postType === 'repost' && item.refPost) {
    const ref = item.refPost;
    const coverUrl = ref.coverUrl || resolveGameShareCoverFallback(item);
    return {
      isVideo: ref.postType === 'video',
      coverUrl,
      videoUrl: ref.videoUrl,
      posterTitle: ref.title?.trim() || resolvePostCardTitle(item),
    };
  }

  const gameShareCover = resolveGameShareCoverFallback(item);
  if (gameShareCover) {
    return {
      isVideo: false,
      coverUrl: gameShareCover,
      posterTitle: resolvePostCardTitle(item),
    };
  }

  return {
    isVideo: postType === 'video',
    coverUrl: item.coverUrl || item.images?.[0],
    videoUrl: item.videoUrl,
    posterTitle: resolvePostCardTitle(item),
  };
}

/** 瀑布流 / 行卡展示标题 */
export function resolvePostCardTitle(item: PostCoverSource): string {
  const type = resolvePostType(item);

  if (item.title?.trim()) return item.title.trim();

  if (type === 'repost' && item.refPost?.title?.trim()) {
    return item.refPost.title.trim();
  }

  const fallback =
    item.content?.trim() ||
    (type === 'repost' ? item.refPost?.summary?.trim() : '') ||
    '';

  return fallback.slice(0, 80) || '分享了一条动态';
}

/** 无图海报配色种子 */
export function resolvePostPosterSeed(item: PostCoverSource): number {
  const numericId = Number(item.id);
  if (Number.isFinite(numericId)) return numericId;

  return (item.id || '')
    .split('')
    .reduce((sum, char) => sum + char.charCodeAt(0), 0);
}
