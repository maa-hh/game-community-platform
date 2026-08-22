import type { PostCoverSource } from '@/utils/postCover';

/** 与后端 NotificationConstants.EventType 对齐 */
export const NOTIFICATION_EVENT = {
  ARTICLE_LIKE: 1,
  ARTICLE_COMMENT: 2,
  COMMENT_REPLY: 3,
  COMMENT_LIKE: 4,
  REPLY_LIKE: 5,
  FOLLOW: 6,
  REPORT_SUBMITTED: 7,
  REPORT_RESULT: 8,
  PENALTY_RESULT: 9,
  FEED_UNREAD: 10,
  PROFILE_AUDIT_PASSED: 11,
  PROFILE_AUDIT_REJECTED: 12,
  PROFILE_AUDIT_HUMAN_REVIEW: 13,
  ARTICLE_FAVORITE: 14,
  ARTICLE_AUDIT_REJECTED: 15,
  ARTICLE_AUDIT_PASSED: 16,
  ARTICLE_AUDIT_HUMAN_REVIEW: 17,
  DANMAKU_COMMENT: 18,
} as const;

export const NOTIFICATION_ROUTE = {
  NONE: 0,
  ARTICLE: 1,
  COMMENT: 2,
  REPLY: 3,
  USER: 4,
  DANMAKU: 5,
} as const;

export type NotificationCategoryKey =
  'system' | 'like_favorite' | 'follow' | 'comment';

export interface INotificationActor {
  accountId: number;
  username?: string;
  avatar?: string;
  action?: 'like' | 'favorite';
}

export interface INotificationMessage {
  id: number;
  eventType: number;
  actorAccountId?: number;
  actorUsername?: string;
  actorAvatar?: string;
  articleId?: number;
  articlePublicId?: string;
  commentId?: number;
  replyId?: number;
  danmakuId?: number;
  videoPublicId?: string;
  reportId?: number;
  targetAccountId?: number;
  previewText?: string;
  resultText?: string;
  /** 帖子封面（赞藏/评论通知右侧缩略图） */
  articleCoverUrl?: string;
  /** 帖子标题（无封面时生成海报） */
  articleTitle?: string;
  /** 与首页信息流同款的封面解析源 */
  articleCoverSource?: PostCoverSource;
  /** 评论/回复正文 */
  contentText?: string;
  /** 当前用户是否已关注通知触发者 */
  actorFollowed?: boolean;
  routeType?: number;
  readStatus?: number;
  readTime?: string;
  createTime?: string;
  aggregated?: boolean;
  aggregateActors?: INotificationActor[];
  aggregateTotal?: number;
  aggregateHasLike?: boolean;
  aggregateHasFavorite?: boolean;
}

export interface INotificationCategorySummary {
  category: NotificationCategoryKey;
  unreadCount: number;
}

export interface INotificationSummary {
  unreadNotificationCount: number;
  feedUnread: boolean;
}
