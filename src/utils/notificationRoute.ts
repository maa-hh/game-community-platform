import type { INotificationMessage } from '@/types/notification';
import { NOTIFICATION_ROUTE } from '@/types/notification';

function resolveProfileAccountId(
  msg: INotificationMessage,
): number | undefined {
  if (msg.targetAccountId) return msg.targetAccountId;
  if (msg.actorAccountId) return msg.actorAccountId;
  const actorWithAccount = msg.aggregateActors?.find(
    (actor) => actor.accountId,
  );
  return actorWithAccount?.accountId;
}

/** 通知点击跳转路径 */
export function buildNotificationLink(
  msg: INotificationMessage,
): string | null {
  const routeType = msg.routeType ?? NOTIFICATION_ROUTE.NONE;
  const articleId = msg.articlePublicId;
  const commentId = msg.commentId;
  const replyId = msg.replyId;
  const accountId = resolveProfileAccountId(msg);

  if (routeType === NOTIFICATION_ROUTE.ARTICLE && articleId) {
    return `/post/${articleId}`;
  }
  if (routeType === NOTIFICATION_ROUTE.COMMENT && articleId && commentId) {
    return `/post/${articleId}?commentId=${commentId}`;
  }
  if (routeType === NOTIFICATION_ROUTE.REPLY && articleId) {
    const params = new URLSearchParams();
    if (commentId) params.set('commentId', String(commentId));
    if (replyId) params.set('replyId', String(replyId));
    const qs = params.toString();
    return qs ? `/post/${articleId}?${qs}` : `/post/${articleId}`;
  }
  if (routeType === NOTIFICATION_ROUTE.USER && accountId) {
    return `/profile?accountId=${accountId}`;
  }
  if (articleId) return `/post/${articleId}`;
  if (accountId) return `/profile?accountId=${accountId}`;
  return null;
}
