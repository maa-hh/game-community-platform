import type { FeedItemData } from '@/types/profile';
import { isGameRefPostId } from '@/utils/gameRepost';

export function isProfileActivityItem(item: FeedItemData): boolean {
  return Boolean(item.refPost && item.activityQuote != null);
}

export function buildPostActivityPath(item: FeedItemData): string | null {
  // 只有评论/点赞等活动项才从 targetArticleId 进入原帖。
  // 普通转发帖必须由调用方使用 item.id（转发帖自身的 publicId），
  // 不能从 refPost.id 取值；游戏分享的 refPost.id 是 game-{appId}。
  if (!isProfileActivityItem(item)) return null;

  const articleId = item.targetArticleId?.trim();
  if (!articleId || isGameRefPostId(articleId)) return null;
  const params = new URLSearchParams();
  if (item.commentId) params.set('commentId', item.commentId);
  if (item.replyId) params.set('replyId', item.replyId);
  const qs = params.toString();
  return `/post/${articleId}${qs ? `?${qs}` : ''}`;
}
