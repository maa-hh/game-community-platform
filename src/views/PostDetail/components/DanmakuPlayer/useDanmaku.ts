import { useCallback, useEffect, useRef, useState } from 'react';

import {
  buildDanmakuWebSocketUrl,
  fetchDanmakuHistoryApi,
  type DanmakuMessage,
  type DanmakuSendPayload,
} from '@/service/danmaku';

import type { DanmakuDensity } from './types';

const WINDOW_MS = 30_000;
const PREFETCH_MARGIN_MS = 6_000;

interface UseDanmakuOptions {
  videoPublicId: string;
  onSendError?: (message: string) => void;
}

interface UseDanmakuResult {
  video: HTMLVideoElement | null;
  enabled: boolean;
  density: DanmakuDensity;
  speed: number;
  playheadMs: number;
  messages: DanmakuMessage[];
  pendingMessages: DanmakuMessage[];
  setVideo: (video: HTMLVideoElement | null) => void;
  setEnabled: (enabled: boolean) => void;
  setDensity: (density: DanmakuDensity) => void;
  setSpeed: (speed: number) => void;
  send: (content: string, videoTimeMs: number) => void;
}

function randomId() {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function mergeMessages(current: DanmakuMessage[], incoming: DanmakuMessage) {
  const id = incoming.id || incoming.clientMessageId;
  const next = current.filter(
    (item) =>
      item.id !== id && item.clientMessageId !== incoming.clientMessageId,
  );
  next.push(incoming);
  next.sort(
    (a, b) =>
      a.displayTimeMs - b.displayTimeMs || Number(a.seq) - Number(b.seq),
  );
  return next.slice(-1500);
}

export function useDanmaku({
  videoPublicId,
  onSendError,
}: UseDanmakuOptions): UseDanmakuResult {
  const [video, setVideoState] = useState<HTMLVideoElement | null>(null);
  const [enabled, setEnabledState] = useState(true);
  const [density, setDensityState] = useState<DanmakuDensity>('medium');
  const [speed, setSpeedState] = useState(1);
  const [playheadMs, setPlayheadMs] = useState(0);
  const [messages, setMessages] = useState<DanmakuMessage[]>([]);
  const socketRef = useRef<WebSocket | null>(null);
  const reconnectTimerRef = useRef<number | null>(null);
  const loadedUntilRef = useRef(0);
  const loadingRef = useRef(false);
  const mountedRef = useRef(true);
  const onSendErrorRef = useRef(onSendError);
  onSendErrorRef.current = onSendError;

  const setVideo = useCallback((next: HTMLVideoElement | null) => {
    setVideoState(next);
  }, []);

  const addMessage = useCallback((message: DanmakuMessage) => {
    if (!mountedRef.current || message.status === 2) return;
    setMessages((current) => {
      const isMine = current.some(
        (item) =>
          item.pending &&
          item.clientMessageId != null &&
          item.clientMessageId === message.clientMessageId,
      );
      return mergeMessages(
        current,
        isMine ? { ...message, isMine: true } : message,
      );
    });
  }, []);

  const loadWindow = useCallback(
    async (fromMs: number, reset = false) => {
      if (loadingRef.current || !mountedRef.current) return;
      loadingRef.current = true;
      try {
        const toMs = fromMs + WINDOW_MS;
        const res = await fetchDanmakuHistoryApi(videoPublicId, fromMs, toMs);
        if (!mountedRef.current) return;
        if (reset) {
          setMessages((res.data || []).filter((item) => item.status !== 2));
        } else {
          (res.data || []).forEach(addMessage);
        }
        loadedUntilRef.current = Math.max(loadedUntilRef.current, toMs);
      } catch {
        // 实时连接仍可工作，历史窗口失败时等下一次预加载重试。
      } finally {
        loadingRef.current = false;
      }
    },
    [addMessage, videoPublicId],
  );

  const closeSocket = useCallback(() => {
    if (reconnectTimerRef.current != null) {
      window.clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = null;
    }
    const socket = socketRef.current;
    socketRef.current = null;
    if (socket) {
      socket.onclose = null;
      socket.close();
    }
  }, []);

  const connectSocket = useCallback(() => {
    if (!enabled || !mountedRef.current) return;
    closeSocket();
    let socket: WebSocket;
    try {
      socket = new WebSocket(buildDanmakuWebSocketUrl(videoPublicId));
    } catch {
      onSendErrorRef.current?.('弹幕连接失败，请稍后重试');
      return;
    }
    socketRef.current = socket;
    socket.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data) as {
          type?: string;
          clientMessageId?: string;
          message?: string;
          data?: DanmakuMessage;
          messageId?: number;
        };
        if (payload.type === 'danmaku' && payload.data) {
          addMessage(payload.data);
          return;
        }
        if (payload.type === 'send_ack' && payload.data) {
          addMessage({ ...payload.data, pending: false, isMine: true });
          return;
        }
        if (payload.type === 'danmaku_removed' && payload.messageId != null) {
          setMessages((current) =>
            current.filter((item) => Number(item.id) !== payload.messageId),
          );
          return;
        }
        if (payload.type === 'send_error') {
          if (payload.clientMessageId) {
            setMessages((current) =>
              current.filter(
                (item) => item.clientMessageId !== payload.clientMessageId,
              ),
            );
          }
          onSendErrorRef.current?.(
            payload.message || '弹幕发送失败，请稍后重试',
          );
        }
      } catch {
        // 忽略单条异常消息，不影响连接和后续弹幕。
      }
    };
    socket.onclose = () => {
      if (!mountedRef.current || !enabled) return;
      reconnectTimerRef.current = window.setTimeout(connectSocket, 1500);
    };
    socket.onerror = () => {
      // onclose 负责重连；不在这里重复提示，避免刷屏。
    };
  }, [addMessage, closeSocket, enabled, videoPublicId]);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      closeSocket();
    };
  }, [closeSocket]);

  useEffect(() => {
    if (!video || !enabled) {
      closeSocket();
      return undefined;
    }
    const current = Math.floor(video.currentTime * 1000);
    setPlayheadMs(current);
    loadedUntilRef.current = current;
    void loadWindow(current, true);
    connectSocket();

    const onSeeked = () => {
      const next = Math.max(0, Math.floor(video.currentTime * 1000));
      loadedUntilRef.current = next;
      void loadWindow(next, true);
    };
    video.addEventListener('seeked', onSeeked);
    const timer = window.setInterval(() => {
      const next = Math.max(0, Math.floor(video.currentTime * 1000));
      setPlayheadMs(next);
      if (next + PREFETCH_MARGIN_MS >= loadedUntilRef.current) {
        void loadWindow(loadedUntilRef.current);
      }
    }, 120);
    return () => {
      video.removeEventListener('seeked', onSeeked);
      window.clearInterval(timer);
    };
  }, [closeSocket, connectSocket, enabled, loadWindow, video]);

  const send = useCallback(
    (content: string, videoTimeMs: number) => {
      const normalized = content.trim();
      if (!normalized) return;
      const clientMessageId = randomId();
      const optimistic: DanmakuMessage = {
        id: `local-${clientMessageId}`,
        clientMessageId,
        videoPublicId,
        videoTimeMs,
        displayTimeMs: videoTimeMs,
        seq: Number.MAX_SAFE_INTEGER,
        accountId: 0,
        content: normalized,
        status: 1,
        isMine: true,
        pending: true,
      };
      setMessages((current) => mergeMessages(current, optimistic));
      const socket = socketRef.current;
      if (!socket || socket.readyState !== WebSocket.OPEN) {
        setMessages((current) =>
          current.filter((item) => item.clientMessageId !== clientMessageId),
        );
        onSendErrorRef.current?.('弹幕连接未就绪，请稍后重试');
        return;
      }
      const payload: DanmakuSendPayload = {
        type: 'send',
        clientMessageId,
        videoTimeMs,
        content: normalized,
      };
      socket.send(JSON.stringify(payload));
    },
    [videoPublicId],
  );

  return {
    video,
    enabled,
    density,
    speed,
    playheadMs,
    messages,
    pendingMessages: messages.filter((item) => item.pending),
    setVideo,
    setEnabled: setEnabledState,
    setDensity: setDensityState,
    setSpeed: setSpeedState,
    send,
  };
}
