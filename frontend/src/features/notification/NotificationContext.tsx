import { createContext, useContext, useEffect, useMemo, useRef, useState } from "react";
import {
  NotificationMessage,
  NotificationRouteType,
  NotificationSseEvent,
  NotificationSummary,
  notificationApi
} from "../../api/notification";
import { useAuth } from "../auth/AuthContext";

type ToastNotification = NotificationMessage & { toastId: string };

type NotificationContextValue = {
  summary: NotificationSummary;
  toasts: ToastNotification[];
  dismissToast: (toastId: string) => void;
  refreshSummary: () => Promise<void>;
  markAllAsRead: () => Promise<void>;
  markFeedRead: () => Promise<void>;
  buildNotificationPath: (message: Pick<NotificationMessage, "routeType" | "articleId" | "commentId" | "replyId" | "targetUserId">) => string | null;
};

const NotificationContext = createContext<NotificationContextValue | null>(null);

const EMPTY_SUMMARY: NotificationSummary = {
  unreadNotificationCount: 0,
  feedUnread: false
};

export function NotificationProvider({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, loading } = useAuth();
  const [summary, setSummary] = useState<NotificationSummary>(EMPTY_SUMMARY);
  const [toasts, setToasts] = useState<ToastNotification[]>([]);
  const eventSourceRef = useRef<EventSource | null>(null);
  const reconnectTimerRef = useRef<number | null>(null);

  async function refreshSummary() {
    if (!isAuthenticated) {
      setSummary(EMPTY_SUMMARY);
      return;
    }
    try {
      setSummary(await notificationApi.getSummary());
    } catch {
      setSummary(EMPTY_SUMMARY);
    }
  }

  async function markAllAsRead() {
    if (!isAuthenticated) {
      setSummary(EMPTY_SUMMARY);
      return;
    }
    try {
      setSummary(await notificationApi.markAllAsRead());
    } catch {
      // ignore and keep local summary
    }
  }

  async function markFeedRead() {
    if (!isAuthenticated || !summary.feedUnread) {
      return;
    }
    try {
      setSummary(await notificationApi.markFeedRead());
    } catch {
      // ignore and keep local summary
    }
  }

  function dismissToast(toastId: string) {
    setToasts((current) => current.filter((item) => item.toastId !== toastId));
  }

  function scheduleReconnect() {
    if (reconnectTimerRef.current != null || !isAuthenticated) {
      return;
    }
    reconnectTimerRef.current = window.setTimeout(() => {
      reconnectTimerRef.current = null;
      connectSse();
    }, 3000);
  }

  function connectSse() {
    eventSourceRef.current?.close();
    const source = notificationApi.createEventSource();
    if (!source) {
      return;
    }
    eventSourceRef.current = source;
    source.onmessage = (event) => {
      handleSseEvent(event.data);
    };
    source.addEventListener("notification_created", (event) => {
      handleSseEvent((event as MessageEvent<string>).data);
    });
    source.addEventListener("notification_summary", (event) => {
      handleSseEvent((event as MessageEvent<string>).data);
    });
    source.addEventListener("feed_unread", (event) => {
      handleSseEvent((event as MessageEvent<string>).data);
    });
    source.onerror = () => {
      source.close();
      if (eventSourceRef.current === source) {
        eventSourceRef.current = null;
      }
      scheduleReconnect();
    };
  }

  function handleSseEvent(raw: string) {
    if (!raw) {
      return;
    }
    try {
      const payload = JSON.parse(raw) as NotificationSseEvent;
      if (payload.summary) {
        setSummary(payload.summary);
      }
      if (payload.eventType === "notification_created" && payload.message) {
        const toast: ToastNotification = {
          ...payload.message,
          toastId: `${payload.message.id}-${Date.now()}`
        };
        setToasts((current) => [toast, ...current].slice(0, 3));
        window.setTimeout(() => {
          setToasts((current) => current.filter((item) => item.toastId !== toast.toastId));
        }, 5000);
      }
    } catch {
      // ignore malformed SSE payload
    }
  }

  useEffect(() => {
    if (loading) {
      return;
    }
    if (!isAuthenticated) {
      eventSourceRef.current?.close();
      eventSourceRef.current = null;
      if (reconnectTimerRef.current != null) {
        window.clearTimeout(reconnectTimerRef.current);
        reconnectTimerRef.current = null;
      }
      setSummary(EMPTY_SUMMARY);
      setToasts([]);
      return;
    }
    void refreshSummary();
    connectSse();
    return () => {
      eventSourceRef.current?.close();
      eventSourceRef.current = null;
      if (reconnectTimerRef.current != null) {
        window.clearTimeout(reconnectTimerRef.current);
        reconnectTimerRef.current = null;
      }
    };
  }, [isAuthenticated, loading]);

  const value = useMemo<NotificationContextValue>(() => ({
    summary,
    toasts,
    dismissToast,
    refreshSummary,
    markAllAsRead,
    markFeedRead,
    buildNotificationPath: (message) => {
      if (message.routeType === NotificationRouteType.USER && message.targetUserId) {
        return `/app/users/id/${message.targetUserId}`;
      }
      if (!message.articleId) {
        return null;
      }
      if (message.routeType === NotificationRouteType.REPLY && message.commentId && message.replyId) {
        return `/app/modules/content/${message.articleId}?commentId=${message.commentId}&replyId=${message.replyId}`;
      }
      if (message.routeType === NotificationRouteType.COMMENT && message.commentId) {
        return `/app/modules/content/${message.articleId}?commentId=${message.commentId}`;
      }
      return `/app/modules/content/${message.articleId}`;
    }
  }), [summary, toasts]);

  return <NotificationContext.Provider value={value}>{children}</NotificationContext.Provider>;
}

export function useNotification() {
  const context = useContext(NotificationContext);
  if (!context) {
    throw new Error("useNotification must be used inside NotificationProvider");
  }
  return context;
}
