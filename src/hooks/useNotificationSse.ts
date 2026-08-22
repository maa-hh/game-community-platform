import { useEffect, useRef } from 'react';
import { App } from 'antd';

import { useAppDispatch, useAppSelector } from '@/store';
import { fetchCurrentUserAction } from '@/store/modules/auth';
import {
  fetchNotificationCategoriesAction,
  receiveRealtimeNotification,
  refetchCategoryIfLoadedAction,
  setNotificationSummary,
} from '@/store/modules/notification';
import {
  createNotificationEventSource,
  isProfileAuditEvent,
  type INotificationSseEvent,
} from '@/service/notification';
import { PROFILE_AUDIT_EVENT } from '@/service/types';
import { isAuthenticated } from '@/utils/storage';
import { resolveNotificationCategory } from '@/utils/notificationCategory';
import type { NotificationCategoryKey } from '@/types/notification';

/**
 * 登录后建立 notification SSE：资料审核 + 消息未读红点
 */
export function useNotificationSse() {
  const dispatch = useAppDispatch();
  const { message } = App.useApp();
  const accessToken = useAppSelector((state) => state.auth.accessToken);
  const activeCategory = useAppSelector(
    (state) => state.notification.activeCategory,
  );
  const activeCategoryRef = useRef<NotificationCategoryKey | null>(null);
  const eventSourceRef = useRef<EventSource | null>(null);
  const reconnectTimerRef = useRef<number | null>(null);

  useEffect(() => {
    activeCategoryRef.current = activeCategory;
  }, [activeCategory]);

  useEffect(() => {
    const clearReconnect = () => {
      if (reconnectTimerRef.current != null) {
        window.clearTimeout(reconnectTimerRef.current);
        reconnectTimerRef.current = null;
      }
    };

    const disconnect = () => {
      eventSourceRef.current?.close();
      eventSourceRef.current = null;
      clearReconnect();
    };

    const handlePayload = (raw: string) => {
      if (!raw) return;
      try {
        const payload = JSON.parse(raw) as INotificationSseEvent;
        if (payload.summary) {
          dispatch(setNotificationSummary(payload.summary));
        }

        if (payload.eventType === 'notification_summary' && payload.summary) {
          void dispatch(fetchNotificationCategoriesAction());
          return;
        }

        const msg = payload.message;
        if (payload.eventType === 'notification_created' && msg) {
          dispatch(
            receiveRealtimeNotification({
              message: msg,
              summary: payload.summary ?? undefined,
            }),
          );
          const category = resolveNotificationCategory(msg.eventType);
          const viewingCategory = activeCategoryRef.current;

          if (category) {
            void dispatch(refetchCategoryIfLoadedAction(category));
            // 正在查看该分类时由页面 effect 标记已读并刷新汇总，避免竞态把红点写回去
            if (category !== viewingCategory) {
              void dispatch(fetchNotificationCategoriesAction());
            }
          } else {
            void dispatch(fetchNotificationCategoriesAction());
          }
          if (isProfileAuditEvent(msg.eventType)) {
            void dispatch(fetchCurrentUserAction());
            if (msg.eventType === PROFILE_AUDIT_EVENT.PASSED) {
              message.success(msg.previewText || '资料审核通过');
            } else if (msg.eventType === PROFILE_AUDIT_EVENT.REJECTED) {
              message.error(
                msg.previewText ||
                  msg.resultText ||
                  '资料审核未通过，请修改后重试',
              );
            } else if (msg.eventType === PROFILE_AUDIT_EVENT.HUMAN_REVIEW) {
              message.info(msg.previewText || '资料已进入人工审核');
            }
          }
          return;
        }
      } catch {
        // ignore malformed SSE
      }
    };

    const connect = () => {
      disconnect();
      if (!isAuthenticated() || !accessToken) return;

      const source = createNotificationEventSource();
      if (!source) return;
      eventSourceRef.current = source;

      source.addEventListener('notification_created', (event) => {
        handlePayload((event as MessageEvent<string>).data);
      });
      source.addEventListener('notification_summary', (event) => {
        handlePayload((event as MessageEvent<string>).data);
      });
      source.addEventListener('feed_unread', (event) => {
        handlePayload((event as MessageEvent<string>).data);
      });
      source.onmessage = (event) => {
        handlePayload(event.data);
      };
      source.onerror = () => {
        source.close();
        if (eventSourceRef.current === source) {
          eventSourceRef.current = null;
        }
        if (!isAuthenticated() || !accessToken) return;
        if (reconnectTimerRef.current != null) return;
        reconnectTimerRef.current = window.setTimeout(() => {
          reconnectTimerRef.current = null;
          connect();
        }, 3000);
      };
    };

    connect();
    return disconnect;
  }, [accessToken, dispatch, message]);
}
