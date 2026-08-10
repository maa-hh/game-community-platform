import hyRequest from '@/service/request';
import { BASE_URL } from './config';
import { getAccessToken } from '@/utils/storage';
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
  return {
    unreadNotificationCount: Number(res.data?.unreadNotificationCount ?? 0),
    feedUnread: Boolean(res.data?.feedUnread),
  };
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

/** SSE：EventSource 无法带 Header，走 query accessToken（网关已支持） */
export function createNotificationEventSource(): EventSource | null {
  const token = getAccessToken();
  if (!token) return null;
  const url = `${BASE_URL}/notification/sse/connect?accessToken=${encodeURIComponent(token)}`;
  return new EventSource(url, { withCredentials: true });
}
