import {
  NOTIFICATION_EVENT,
  type NotificationCategoryKey,
} from '@/types/notification';

/** 与后端 NotificationCategory.eventTypesOf 对齐 */
export function resolveNotificationCategory(
  eventType?: number,
): NotificationCategoryKey | null {
  if (eventType == null) return null;

  switch (eventType) {
    case NOTIFICATION_EVENT.ARTICLE_LIKE:
    case NOTIFICATION_EVENT.COMMENT_LIKE:
    case NOTIFICATION_EVENT.REPLY_LIKE:
    case NOTIFICATION_EVENT.ARTICLE_FAVORITE:
      return 'like_favorite';
    case NOTIFICATION_EVENT.FOLLOW:
      return 'follow';
    case NOTIFICATION_EVENT.ARTICLE_COMMENT:
    case NOTIFICATION_EVENT.COMMENT_REPLY:
    case NOTIFICATION_EVENT.DANMAKU_COMMENT:
      return 'comment';
    case NOTIFICATION_EVENT.REPORT_SUBMITTED:
    case NOTIFICATION_EVENT.REPORT_RESULT:
    case NOTIFICATION_EVENT.PENALTY_RESULT:
    case NOTIFICATION_EVENT.PROFILE_AUDIT_PASSED:
    case NOTIFICATION_EVENT.PROFILE_AUDIT_REJECTED:
    case NOTIFICATION_EVENT.PROFILE_AUDIT_HUMAN_REVIEW:
    case NOTIFICATION_EVENT.ARTICLE_AUDIT_REJECTED:
    case NOTIFICATION_EVENT.ARTICLE_AUDIT_PASSED:
    case NOTIFICATION_EVENT.ARTICLE_AUDIT_HUMAN_REVIEW:
      return 'system';
    default:
      return null;
  }
}
