import { API_PREFIX, request, requestEnvelope, tokenStore } from "./client";

export type NotificationSummary = {
  unreadNotificationCount: number;
  feedUnread: boolean;
};

export type NotificationMessage = {
  id: number;
  eventType: number;
  actorUserId?: number;
  actorUsername?: string;
  actorAvatar?: string;
  articleId?: number;
  commentId?: number;
  replyId?: number;
  reportId?: number;
  targetUserId?: number;
  previewText?: string;
  resultText?: string;
  routeType: number;
  readStatus: number;
  readTime?: string;
  createTime?: string;
};

export type NotificationPage = {
  code: number;
  message: string;
  data: NotificationMessage[];
  page: number;
  size: number;
  total: number;
};

export type NotificationSseEvent = {
  eventType: string;
  summary?: NotificationSummary | null;
  message?: NotificationMessage | null;
};

export const NotificationRouteType = {
  NONE: 0,
  ARTICLE: 1,
  COMMENT: 2,
  REPLY: 3,
  USER: 4
} as const;

export const notificationApi = {
  getSummary: () => request<NotificationSummary>("/notification/summary"),
  listMessages: (page = 1, size = 20, eventType?: number) => {
    const params = new URLSearchParams();
    params.set("page", String(page));
    params.set("size", String(size));
    if (eventType != null) {
      params.set("eventType", String(eventType));
    }
    return requestEnvelope<NotificationMessage[]>(`/notification/messages?${params.toString()}`) as Promise<NotificationPage>;
  },
  markAllAsRead: () => request<NotificationSummary>("/notification/messages/read-all", { method: "PUT" }),
  markFeedRead: () => request<NotificationSummary>("/notification/feed/read", { method: "PUT" }),
  createEventSource: () => {
    const token = tokenStore.get();
    if (!token) {
      return null;
    }
    const url = `${API_PREFIX}/notification/sse/connect?accessToken=${encodeURIComponent(token)}`;
    return new EventSource(url, { withCredentials: true });
  }
};
