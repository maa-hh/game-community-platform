import { useEffect, useRef } from 'react';
import { message } from 'antd';

import { useAppDispatch, useAppSelector } from '@/store';
import { fetchCurrentUserAction } from '@/store/modules/auth';
import {
  createNotificationEventSource,
  isProfileAuditEvent,
  type INotificationSseEvent,
} from '@/service/notification';
import { PROFILE_AUDIT_EVENT } from '@/service/types';
import { isAuthenticated } from '@/utils/storage';

/**
 * 登录后建立 notification SSE，资料审核结果推送后同步 /user/me（不轮询）
 */
export function useProfileAuditSse() {
  const dispatch = useAppDispatch();
  const accessToken = useAppSelector((state) => state.auth.accessToken);
  const eventSourceRef = useRef<EventSource | null>(null);
  const reconnectTimerRef = useRef<number | null>(null);

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
        const msg = payload.message;
        if (
          payload.eventType !== 'notification_created' ||
          !msg ||
          !isProfileAuditEvent(msg.eventType)
        ) {
          return;
        }

        void dispatch(fetchCurrentUserAction());

        if (msg.eventType === PROFILE_AUDIT_EVENT.PASSED) {
          message.success(msg.previewText || '资料审核通过');
        } else if (msg.eventType === PROFILE_AUDIT_EVENT.REJECTED) {
          message.error(
            msg.previewText || msg.resultText || '资料审核未通过，请修改后重试',
          );
        } else if (msg.eventType === PROFILE_AUDIT_EVENT.HUMAN_REVIEW) {
          message.info(msg.previewText || '资料已进入人工审核');
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
  }, [accessToken, dispatch]);
}
