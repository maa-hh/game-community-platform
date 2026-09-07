import {
  NOTIFICATION_EVENT,
  type INotificationMessage,
} from '@/types/notification';
import { resolveNotificationCategory } from '@/utils/notificationCategory';

export type NotificationItemVariant =
  'like_favorite' | 'follow' | 'comment' | 'system';

export function resolveNotificationItemVariant(
  item: INotificationMessage,
): NotificationItemVariant {
  const category = resolveNotificationCategory(item.eventType);
  if (category === 'like_favorite') return 'like_favorite';
  if (category === 'follow') return 'follow';
  if (category === 'comment') return 'comment';
  return 'system';
}

/** 通知动作文案（不含昵称） */
export function getNotificationActionText(
  eventType?: number,
  aggregated?: boolean,
  aggregateAction?: 'like' | 'favorite' | 'mixed',
): string {
  if (aggregated) {
    if (aggregateAction === 'mixed') return '赞和收藏了你的帖子';
    return aggregateAction === 'favorite' ? '收藏了你的帖子' : '赞了你的帖子';
  }

  switch (eventType) {
    case NOTIFICATION_EVENT.ARTICLE_LIKE:
      return '赞了你的帖子';
    case NOTIFICATION_EVENT.ARTICLE_FAVORITE:
      return '收藏了你的帖子';
    case NOTIFICATION_EVENT.COMMENT_LIKE:
      return '赞了你的评论';
    case NOTIFICATION_EVENT.REPLY_LIKE:
      return '赞了你的回复';
    case NOTIFICATION_EVENT.FOLLOW:
      return '关注了你';
    case NOTIFICATION_EVENT.ARTICLE_COMMENT:
      return '评论了你的帖子';
    case NOTIFICATION_EVENT.COMMENT_REPLY:
      return '回复了你';
    case NOTIFICATION_EVENT.DANMAKU_COMMENT:
      return '发了弹幕';
    case NOTIFICATION_EVENT.GAME_REVIEW_REPLY:
      return '回复了你的游戏评价';
    case NOTIFICATION_EVENT.GAME_REVIEW_LIKE:
      return '赞了你的游戏评价';
    case NOTIFICATION_EVENT.GAME_REVIEW_REPLY_LIKE:
      return '赞了你的评价回复';
    default:
      return '';
  }
}

function stripNotificationSummary(
  text: string,
  item: INotificationMessage,
): string {
  const action = getNotificationActionText(item.eventType);
  const nickname = item.actorUsername?.trim();
  const candidates = [
    text,
    action && text === action ? '' : text,
    nickname && action ? `${nickname} ${action}` : '',
    nickname && action ? `${nickname}${action}` : '',
  ].filter(Boolean);

  for (const candidate of candidates) {
    if (text === candidate) return '';
  }

  if (action) {
    const actionIndex = text.indexOf(action);
    if (actionIndex >= 0) {
      const after = text
        .slice(actionIndex + action.length)
        .trim()
        .replace(/^[：:\s]+/, '');
      if (after) return after;

      const before = text.slice(0, actionIndex).trim();
      if (!before || before === nickname) return '';
    }
  }

  if (
    /^(评论了你的帖子|回复了你|发了弹幕|赞了你的帖子|收藏了你的帖子|赞了你的评论|赞了你的回复)$/.test(
      text,
    )
  ) {
    return '';
  }

  if (nickname) {
    const prefix = new RegExp(
      `^${nickname.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*`,
    );
    const stripped = text.replace(prefix, '').trim();
    if (
      stripped === action ||
      /^(评论了你的帖子|回复了你|发了弹幕)$/.test(stripped)
    ) {
      return '';
    }
  }

  return text;
}

export function resolveNotificationContent(item: INotificationMessage): string {
  if (item.eventType === NOTIFICATION_EVENT.DANMAKU_COMMENT) {
    return item.resultText?.trim() || '';
  }
  if (item.eventType === NOTIFICATION_EVENT.GAME_REVIEW_REPLY) {
    return item.resultText?.trim() || item.contentText?.trim() || '';
  }
  if (item.contentText?.trim()) return item.contentText.trim();

  const text = item.previewText?.trim() || '';
  if (!text) return '';

  return stripNotificationSummary(text, item);
}

export function resolveNotificationQuote(item: INotificationMessage): string {
  if (item.eventType !== NOTIFICATION_EVENT.COMMENT_REPLY) return '';
  const quote = item.resultText?.trim() || '';
  if (!quote) return '';
  return stripNotificationSummary(quote, item) || quote;
}

export function resolveNotificationCoverTitle(
  item: INotificationMessage,
): string {
  return (
    item.articleTitle?.trim() ||
    item.gameTitle?.trim() ||
    (item.gameAppId ? `游戏 ${item.gameAppId}` : '帖子')
  );
}
