import { ARTICLE_STATUS } from '@/service/content';
import type { ContentCardAuthor } from '@/types/content';
import type { PostRefCard } from '@/types/post';
import { resolveDisplayCommentCount } from '@/utils/commentCount';
import {
  type IArticleRaw,
  type IArticleStatsRaw,
  resolveArticleCoverUrl,
} from '@/utils/mapPost';
import { sanitizeGameShareContent } from '@/utils/gameRepost';
import { mapNumericPostType } from '@/utils/postType';

export type PostRefUnavailableReason = 'deleted' | 'offline' | 'unavailable';

const UNAVAILABLE_TITLE: Record<PostRefUnavailableReason, string> = {
  deleted: '原帖已删除',
  offline: '原帖已下架',
  unavailable: '原帖暂不可用',
};

const UNAVAILABLE_SUMMARY = '内容可能已被作者删除或下架，请稍后刷新再试';

/** 对外可见的已发布帖 */
export function isArticlePubliclyVisible(status?: number): boolean {
  return status === ARTICLE_STATUS.PUBLISHED;
}

export function resolveUnavailableReason(
  status?: number,
): PostRefUnavailableReason {
  if (status === ARTICLE_STATUS.OFFLINE) return 'offline';
  if (
    status === ARTICLE_STATUS.DRAFT ||
    status === ARTICLE_STATUS.PENDING ||
    status === ARTICLE_STATUS.REJECTED
  ) {
    return 'offline';
  }
  return 'deleted';
}

export function resolveRepostRefPost(
  refArticleId: string | undefined,
  refPost?: PostRefCard,
): PostRefCard | undefined {
  if (!refArticleId) return refPost;
  if (refPost) return refPost;
  return buildUnavailableRefPost(refArticleId, 'unavailable');
}
export function buildUnavailableRefPost(
  articleId: number | string,
  reason: PostRefUnavailableReason = 'unavailable',
  fallbackTitle?: string,
): PostRefCard {
  return {
    id: String(articleId),
    title: fallbackTitle?.trim() || UNAVAILABLE_TITLE[reason],
    summary: UNAVAILABLE_SUMMARY,
    postType: 'image_text',
    author: { accountId: 0, nickname: '—' },
    unavailable: true,
    unavailableReason: reason,
    unavailableMessage: UNAVAILABLE_TITLE[reason],
    viewCount: 0,
    commentCount: 0,
    likeCount: 0,
    liked: false,
  };
}

export function attachRefPostStats(
  ref: PostRefCard,
  stats?: IArticleStatsRaw | null,
): PostRefCard {
  if (!stats || ref.unavailable) return ref;
  return {
    ...ref,
    viewCount: Number(stats.viewCount || 0),
    commentCount: resolveDisplayCommentCount(stats),
    likeCount: Number(stats.likeCount || 0),
    liked: Boolean(stats.liked),
  };
}

export function buildRefPostFromArticleRaw(
  raw: IArticleRaw & { imageUrls?: string[] },
  author: ContentCardAuthor,
  stats?: IArticleStatsRaw | null,
): PostRefCard {
  if (!isArticlePubliclyVisible(raw.status)) {
    return buildUnavailableRefPost(
      raw.publicId,
      resolveUnavailableReason(raw.status),
      raw.title,
    );
  }

  const postType = mapNumericPostType(raw.postType);
  const summary =
    sanitizeGameShareContent({ summary: raw.summary }) || raw.summary;
  const ref: PostRefCard = {
    id: raw.publicId,
    title: raw.title,
    summary,
    coverUrl: resolveArticleCoverUrl(raw),
    videoUrl: raw.videoUrl,
    postType: postType === 'repost' ? 'image_text' : postType,
    author: {
      accountId: author.accountId,
      nickname: author.nickname,
      avatar: author.avatar,
    },
  };
  return attachRefPostStats(ref, stats);
}
