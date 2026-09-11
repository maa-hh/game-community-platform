import { useEffect, useRef } from 'react';
import { App } from 'antd';

import { useAppDispatch, useAppSelector } from '@/store';
import { fetchCurrentUserAction } from '@/store/modules/auth';
import {
  markCategoryRefreshRequired,
  incrementFeedUnread,
  incrementNotificationUnread,
  patchNotificationSummary,
  resetNotificationState,
  setNotificationSummary,
} from '@/store/modules/notification';
import {
  markFollowFeedReloadRequired,
  markProfileDataDirty,
  resetProfileRealtime,
} from '@/store/modules/profileRealtime';
import {
  createNotificationEventSource,
  isProfileAuditEvent,
  parseNotificationSseEvent,
} from '@/service/notification';
import { PROFILE_AUDIT_EVENT } from '@/service/types';
import { NOTIFICATION_EVENT } from '@/types/notification';
import { getAccessToken, isAuthenticated } from '@/utils/storage';
import { resolveNotificationCategory } from '@/utils/notificationCategory';
import {
  isProfileDataDomain,
  PROFILE_DATA_DOMAIN,
  type ProfileDataDomain,
} from '@/types/profileRealtime';
import { invalidateProfileDataCaches } from '@/utils/profileDataCache';

/** 登录后建立 notification SSE：资料审核 + 消息未读红点。 */
export function useNotificationSse() {
  const dispatch = useAppDispatch();
  const { message } = App.useApp();
  const accessToken = useAppSelector((state) => state.auth.accessToken);
  const accountId = useAppSelector((state) => state.auth.user?.accountId);
  const eventSourceRef = useRef<EventSource | null>(null);
  const reconnectTimerRef = useRef<number | null>(null);
  const accountScopeRef = useRef<number | null | undefined>(undefined);
  const pendingProfileInvalidationsRef = useRef<ProfileDataDomain[]>([]);

  useEffect(() => {
    if (
      accountScopeRef.current !== undefined &&
      accountScopeRef.current !== (accountId ?? null)
    ) {
      pendingProfileInvalidationsRef.current = [];
      dispatch(resetNotificationState());
      dispatch(resetProfileRealtime());
    }
    accountScopeRef.current = accountId ?? null;
  }, [accountId, dispatch]);

  useEffect(() => {
    if (!accountId || pendingProfileInvalidationsRef.current.length === 0) {
      return;
    }
    dispatch(
      markProfileDataDirty({
        accountId,
        domains: pendingProfileInvalidationsRef.current,
      }),
    );
    invalidateProfileDataCaches(
      accountId,
      pendingProfileInvalidationsRef.current,
    );
    pendingProfileInvalidationsRef.current = [];
  }, [accountId, dispatch]);

  useEffect(() => {
    const clearReconnect = () => {
      if (reconnectTimerRef.current != null) {
        window.clearTimeout(reconnectTimerRef.current);
        reconnectTimerRef.current = null;
      }
    };

    let disposed = false;

    const disconnect = () => {
      eventSourceRef.current?.close();
      eventSourceRef.current = null;
      clearReconnect();
    };

    const handlePayload = (raw: string, eventType?: string) => {
      if (!raw) return;

      try {
        const payload = parseNotificationSseEvent(raw, eventType);
        if (!payload) return;

        const isFeedUnreadEvent =
          payload.eventType === 'feed_unread' ||
          payload.message?.eventType === NOTIFICATION_EVENT.FEED_UNREAD;
        if (payload.summary) {
          if (isFeedUnreadEvent) {
            dispatch(
              patchNotificationSummary({
                feedUnread: true,
                feedUnreadCount: Math.max(
                  1,
                  Number(payload.summary.feedUnreadCount || 0),
                ),
              }),
            );
          } else if (payload.eventType === 'notification_created') {
            dispatch(
              patchNotificationSummary({
                unreadNotificationCount:
                  payload.summary.unreadNotificationCount,
              }),
            );
          } else {
            dispatch(setNotificationSummary(payload.summary));
          }
        } else if (isFeedUnreadEvent) {
          dispatch(incrementFeedUnread());
        } else if (payload.eventType === 'notification_created') {
          dispatch(incrementNotificationUnread());
        }

        if (isFeedUnreadEvent) {
          dispatch(markFollowFeedReloadRequired());
        }

        if (payload.eventType === 'notification_summary' && payload.summary) {
          return;
        }

        if (payload.eventType === 'profile_invalidated') {
          const domains = (payload.invalidationDomains ?? []).filter(
            isProfileDataDomain,
          );
          if (domains.length > 0) {
            if (accountId) {
              dispatch(markProfileDataDirty({ accountId, domains }));
              invalidateProfileDataCaches(accountId, domains);
            } else {
              pendingProfileInvalidationsRef.current = Array.from(
                new Set([
                  ...pendingProfileInvalidationsRef.current,
                  ...domains,
                ]),
              );
            }
          }
          if (
            domains.includes(PROFILE_DATA_DOMAIN.FOLLOWING) ||
            domains.includes(PROFILE_DATA_DOMAIN.FEED)
          ) {
            dispatch(markFollowFeedReloadRequired());
          }
          if (domains.includes(PROFILE_DATA_DOMAIN.BASE)) return;
          return;
        }

        const msg = payload.message;
        if (payload.eventType !== 'notification_created' || !msg) return;

        const category = resolveNotificationCategory(msg.eventType);
        if (category) dispatch(markCategoryRefreshRequired({ category }));

        if (isProfileAuditEvent(msg.eventType)) {
          void dispatch(fetchCurrentUserAction());
          if (msg.eventType === PROFILE_AUDIT_EVENT.PASSED) {
            message.success(msg.previewText || '资料审核通过');
          } else if (msg.eventType === PROFILE_AUDIT_EVENT.REJECTED) {
            message.error(
              msg.resultText || msg.previewText || '资料未通过，请修改后重试',
            );
          } else if (msg.eventType === PROFILE_AUDIT_EVENT.HUMAN_REVIEW) {
            message.info(msg.previewText || '资料已进入人工审核');
          }
        }
      } catch {
        // 忽略无法识别的 SSE 数据，避免中断连接。
      }
    };

    const scheduleReconnect = () => {
      if (!isAuthenticated() || !getAccessToken()) return;
      if (reconnectTimerRef.current != null) return;
      reconnectTimerRef.current = window.setTimeout(() => {
        reconnectTimerRef.current = null;
        void connect();
      }, 3000);
    };

    const connect = async () => {
      disconnect();
      if (!isAuthenticated() || !accessToken) return;

      let source: EventSource | null = null;
      try {
        source = await createNotificationEventSource();
      } catch {
        scheduleReconnect();
        return;
      }
      if (disposed) {
        source?.close();
        return;
      }
      if (!source) {
        scheduleReconnect();
        return;
      }

      const eventSource = source;
      eventSourceRef.current = eventSource;
      const handleEvent = (type: string, event: Event) => {
        handlePayload((event as MessageEvent<string>).data, type);
      };

      eventSource.addEventListener('notification_created', (event) => {
        handleEvent('notification_created', event);
      });
      eventSource.addEventListener('notification_summary', (event) => {
        handleEvent('notification_summary', event);
      });
      eventSource.addEventListener('feed_unread', (event) => {
        handleEvent('feed_unread', event);
      });
      eventSource.addEventListener('profile_invalidated', (event) => {
        handleEvent('profile_invalidated', event);
      });
      eventSource.onmessage = (event) => {
        handleEvent('message', event);
      };
      eventSource.onerror = () => {
        eventSource.close();
        if (eventSourceRef.current === eventSource) {
          eventSourceRef.current = null;
        }
        scheduleReconnect();
      };
    };

    void connect();
    return () => {
      disposed = true;
      disconnect();
    };
  }, [accessToken, accountId, dispatch, message]);
}
