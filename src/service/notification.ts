import hyRequest, { refreshAccessTokenForSse } from '@/service/request';
import { ACCESS_REFRESH_BUFFER_MS, BASE_URL } from './config';
import { getAccessToken, isAccessTokenExpired } from '@/utils/storage';
import { PROFILE_AUDIT_EVENT } from './types';
import type {
  INotificationCategorySummary,
  INotificationMessage,
  INotificationSummary,
} from '@/types/notification';
import type { IPageResult } from './types';

export type {
  INotificationCategorySummary,
  INotificationMessage,
  INotificationSummary,
} from '@/types/notification';

export interface INotificationSseEvent {
  eventType: string;
  summary?: INotificationSummary | null;
  message?: INotificationMessage | null;
  eventId?: string | null;
  invalidationDomains?: string[] | null;
}

type UnknownRecord = Record<string, unknown>;

function isRecord(value: unknown): value is UnknownRecord {
  return typeof value === 'object' && value !== null;
}

function isNotificationMessage(value: unknown): value is INotificationMessage {
  return (
    isRecord(value) &&
    typeof value.id === 'number' &&
    typeof value.eventType === 'number'
  );
}

function isNotificationSummary(value: unknown): value is INotificationSummary {
  return (
    isRecord(value) &&
    ('unreadNotificationCount' in value ||
      'feedUnread' in value ||
      'feedUnreadCount' in value)
  );
}

function normalizeBoolean(value: unknown): boolean {
  if (typeof value === 'boolean') return value;
  if (typeof value === 'number') return value !== 0;
  if (typeof value === 'string') {
    return ['true', '1', 'yes'].includes(value.trim().toLowerCase());
  }
  return false;
}

function normalizeCount(value: unknown, fallback = 0): number {
  const count = Number(value);
  return Number.isFinite(count) && count >= 0 ? count : fallback;
}

/** 统一处理 SSE/接口可能返回的数字字段，避免红点因字符串或缺省字段失效。 */
export function normalizeNotificationSummary(
  value: unknown,
): INotificationSummary | null {
  if (!isNotificationSummary(value)) return null;

  const feedUnread = normalizeBoolean(value.feedUnread);
  const feedUnreadCount = normalizeCount(
    value.feedUnreadCount,
    feedUnread ? 1 : 0,
  );
  return {
    unreadNotificationCount: normalizeCount(value.unreadNotificationCount),
    feedUnread,
    feedUnreadCount,
  };
}

/** 兼容当前 SSE 结构、旧 data 包装结构以及直接通知对象结构。 */
export function parseNotificationSseEvent(
  raw: string,
  fallbackEventType?: string,
): INotificationSseEvent | null {
  if (!raw) return null;

  const parsed = JSON.parse(raw) as unknown;
  const root = isRecord(parsed) && isRecord(parsed.data) ? parsed.data : parsed;

  if (isNotificationMessage(root)) {
    return { eventType: 'notification_created', message: root };
  }
  if (!isRecord(root)) return null;

  const eventType =
    typeof root.eventType === 'string'
      ? root.eventType
      : fallbackEventType === 'message'
        ? undefined
        : fallbackEventType;
  // 兼容标准事件 { summary } 与旧版直接发送 summary 对象的格式。
  const summary =
    normalizeNotificationSummary(root.summary) ??
    normalizeNotificationSummary(root);
  const message = isNotificationMessage(root.message)
    ? root.message
    : undefined;

  if (!eventType && !summary && !message) return null;

  return {
    eventType: eventType ?? (message ? 'notification_created' : 'message'),
    summary,
    message,
    eventId: typeof root.eventId === 'string' ? root.eventId : null,
    invalidationDomains: Array.isArray(root.invalidationDomains)
      ? root.invalidationDomains.filter(
          (item): item is string => typeof item === 'string',
        )
      : null,
  };
}

export function isProfileAuditEvent(eventType?: number): boolean {
  return (
    eventType === PROFILE_AUDIT_EVENT.PASSED ||
    eventType === PROFILE_AUDIT_EVENT.REJECTED ||
    eventType === PROFILE_AUDIT_EVENT.HUMAN_REVIEW
  );
}

export async function fetchNotificationSummaryApi(): Promise<INotificationSummary> {
  const res = await hyRequest.get<{ data: INotificationSummary }>({
    url: '/notification/summary',
  });
  return (
    normalizeNotificationSummary(res.data) ?? {
      unreadNotificationCount: 0,
      feedUnread: false,
      feedUnreadCount: 0,
    }
  );
}

export async function markFeedReadApi(): Promise<INotificationSummary> {
  const res = await hyRequest.put<{ data: INotificationSummary }>({
    url: '/notification/feed/read',
  });
  return (
    normalizeNotificationSummary(res.data) ?? {
      unreadNotificationCount: 0,
      feedUnread: false,
      feedUnreadCount: 0,
    }
  );
}

export async function fetchNotificationCategorySummariesApi(): Promise<
  INotificationCategorySummary[]
> {
  const res = await hyRequest.get<{ data: INotificationCategorySummary[] }>({
    url: '/notification/summary/categories',
  });
  return (res.data || []).map((item) => ({
    ...item,
    unreadCount: Number(item.unreadCount ?? 0),
  }));
}

export async function fetchNotificationMessagesApi(params: {
  category: string;
  page?: number;
  size?: number;
}): Promise<IPageResult<INotificationMessage>> {
  const res = await hyRequest.get<IPageResult<INotificationMessage>>({
    url: '/notification/messages',
    params: {
      page: params.page ?? 1,
      size: params.size ?? 20,
      category: params.category,
    },
  });
  return {
    ...res,
    data: res.data || [],
  };
}

export async function markNotificationCategoryReadApi(
  category: string,
): Promise<INotificationSummary> {
  const res = await hyRequest.put<{ data: INotificationSummary }>({
    url: '/notification/messages/read-category',
    params: { category },
  });
  return res.data;
}

export async function markAllNotificationsReadApi(): Promise<INotificationSummary> {
  const res = await hyRequest.put<{ data: INotificationSummary }>({
    url: '/notification/messages/read-all',
  });
  return res.data;
}

/** SSE：EventSource 无法带 Header，走 query accessToken（网关已支持）。 */
export async function createNotificationEventSource(): Promise<EventSource | null> {
  if (isAccessTokenExpired(ACCESS_REFRESH_BUFFER_MS)) {
    await refreshAccessTokenForSse();
  }

  const token = getAccessToken();
  if (!token) return null;
  const url = `${BASE_URL}/notification/sse/connect?accessToken=${encodeURIComponent(token)}`;
  return new EventSource(url);
}
