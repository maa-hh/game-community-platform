import { useCallback, useEffect, useRef, useState } from 'react';

import {
  buildDanmakuWebSocketUrl,
  fetchDanmakuHistoryApi,
  fetchDanmakuMessageApi,
  type DanmakuMessage,
  type DanmakuSendPayload,
} from '@/service/danmaku';

import type { DanmakuDensity } from './types';

const WINDOW_MS = 30_000;
const PREFETCH_MARGIN_MS = 6_000;

interface UseDanmakuOptions {
  videoPublicId: string;
  targetDanmakuId?: number;
  accountId?: number;
  onSendError?: (message: string) => void;
}

interface UseDanmakuResult {
  video: HTMLVideoElement | null;
  isPlaying: boolean;
  playbackRate: number;
  timelineRevision: number;
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
  const hasClientMessageId = Boolean(incoming.clientMessageId);
  const next = current.filter(
    (item) =>
      item.id !== id &&
      (!hasClientMessageId ||
        item.clientMessageId !== incoming.clientMessageId),
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
  targetDanmakuId,
  accountId,
  onSendError,
}: UseDanmakuOptions): UseDanmakuResult {
  const [video, setVideoState] = useState<HTMLVideoElement | null>(null);
  const [enabled, setEnabledState] = useState(true);
  const [density, setDensityState] = useState<DanmakuDensity>('medium');
  const [speed, setSpeedState] = useState(1);
  const [isPlaying, setIsPlaying] = useState(false);
  const [playbackRate, setPlaybackRate] = useState(1);
  const [timelineRevision, setTimelineRevision] = useState(0);
  const [playheadMs, setPlayheadMs] = useState(0);
  const [messages, setMessages] = useState<DanmakuMessage[]>([]);
  const socketRef = useRef<WebSocket | null>(null);
  const reconnectTimerRef = useRef<number | null>(null);
  const loadedUntilRef = useRef(0);
  const loadingRef = useRef(false);
  const loadVersionRef = useRef(0);
  const pendingLoadRef = useRef<{ fromMs: number; reset: boolean } | null>(
    null,
  );
  const targetSeekKeyRef = useRef<string | null>(null);
  const loadWindowRef = useRef<
    ((fromMs: number, reset?: boolean) => Promise<void>) | null
  >(null);
  const mountedRef = useRef(true);
  const isPlayingRef = useRef(false);
  const previousTimeRef = useRef<number | null>(null);
  const onSendErrorRef = useRef(onSendError);
  onSendErrorRef.current = onSendError;
  isPlayingRef.current = isPlaying;

  const markMine = useCallback(
    (message: DanmakuMessage): DanmakuMessage => ({
      ...message,
      isMine:
        accountId != null && message.accountId != null
          ? Number(message.accountId) === Number(accountId)
          : Boolean(message.isMine),
    }),
    [accountId],
  );

  const setVideo = useCallback((next: HTMLVideoElement | null) => {
    setVideoState(next);
  }, []);

  const addMessage = useCallback(
    (message: DanmakuMessage) => {
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
          markMine(isMine ? { ...message, isMine: true } : message),
        );
      });
    },
    [markMine],
  );

  const loadWindow = useCallback(
    async (fromMs: number, reset = false) => {
      if (!mountedRef.current) return;
      if (loadingRef.current) {
        if (reset) {
          pendingLoadRef.current = { fromMs, reset: true };
          loadVersionRef.current += 1;
        }
        return;
      }
      loadingRef.current = true;
      const loadVersion = ++loadVersionRef.current;
      try {
        const toMs = fromMs + WINDOW_MS;
        const res = await fetchDanmakuHistoryApi(videoPublicId, fromMs, toMs);
        if (!mountedRef.current || loadVersion !== loadVersionRef.current) {
          return;
        }
        if (reset) {
          setMessages(
            (res.data || []).filter((item) => item.status !== 2).map(markMine),
          );
        } else {
          (res.data || []).forEach(addMessage);
        }
        loadedUntilRef.current = Math.max(loadedUntilRef.current, toMs);
      } catch {
        // 实时连接仍可工作，历史窗口失败时等下一次预加载重试。
      } finally {
        loadingRef.current = false;
        const pendingLoad = pendingLoadRef.current;
        pendingLoadRef.current = null;
        if (pendingLoad && mountedRef.current) {
          void loadWindowRef.current?.(pendingLoad.fromMs, pendingLoad.reset);
        }
      }
    },
    [addMessage, markMine, videoPublicId],
  );
  loadWindowRef.current = loadWindow;

  const resetTimeline = useCallback(
    (nextTimeMs: number) => {
      previousTimeRef.current = nextTimeMs;
      loadedUntilRef.current = nextTimeMs;
      setTimelineRevision((current) => current + 1);
      void loadWindow(nextTimeMs, true);
    },
    [loadWindow],
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
    if (!video) {
      setIsPlaying(false);
      setPlaybackRate(1);
      return undefined;
    }

    const syncPlaybackState = () => {
      setIsPlaying(!video.paused && !video.ended);
    };
    const syncPlaybackRate = () => {
      setPlaybackRate(video.playbackRate || 1);
    };

    syncPlaybackState();
    syncPlaybackRate();
    video.addEventListener('play', syncPlaybackState);
    video.addEventListener('playing', syncPlaybackState);
    video.addEventListener('pause', syncPlaybackState);
    video.addEventListener('ended', syncPlaybackState);
    video.addEventListener('ratechange', syncPlaybackRate);

    return () => {
      video.removeEventListener('play', syncPlaybackState);
      video.removeEventListener('playing', syncPlaybackState);
      video.removeEventListener('pause', syncPlaybackState);
      video.removeEventListener('ended', syncPlaybackState);
      video.removeEventListener('ratechange', syncPlaybackRate);
    };
  }, [video]);

  useEffect(() => {
    if (!video || targetDanmakuId == null) return undefined;
    const targetKey = `${videoPublicId}:${targetDanmakuId}`;
    if (targetSeekKeyRef.current === targetKey) return undefined;
    targetSeekKeyRef.current = targetKey;
    let cancelled = false;

    const seekToDanmaku = (displayTimeMs: number) => {
      if (cancelled || !mountedRef.current) return;
      video.currentTime = Math.max(0, displayTimeMs) / 1000;
    };

    const loadTarget = async () => {
      try {
        const res = await fetchDanmakuMessageApi(
          videoPublicId,
          targetDanmakuId,
        );
        const target = res.data;
        if (cancelled || !target) return;
        const displayTimeMs = Number(
          target.displayTimeMs ?? target.videoTimeMs ?? 0,
        );
        if (!Number.isFinite(displayTimeMs)) return;
        if (video.readyState >= 1) {
          seekToDanmaku(displayTimeMs);
        } else {
          video.addEventListener(
            'loadedmetadata',
            () => seekToDanmaku(displayTimeMs),
            { once: true },
          );
        }
      } catch {
        targetSeekKeyRef.current = null;
      }
    };

    void loadTarget();
    return () => {
      cancelled = true;
    };
  }, [targetDanmakuId, video, videoPublicId]);

  useEffect(() => {
    if (!video || !enabled) {
      closeSocket();
      return undefined;
    }
    const current = Math.floor(video.currentTime * 1000);
    previousTimeRef.current = current;
    setPlayheadMs(current);
    loadedUntilRef.current = current;
    void loadWindow(current, true);
    connectSocket();

    const onSeeked = () => {
      const next = Math.max(0, Math.floor(video.currentTime * 1000));
      resetTimeline(next);
      setPlayheadMs(next);
    };
    video.addEventListener('seeked', onSeeked);
    const timer = window.setInterval(() => {
      const next = Math.max(0, Math.floor(video.currentTime * 1000));
      const previous = previousTimeRef.current;
      const looped = previous != null && next + 500 < previous;
      if (looped) resetTimeline(next);
      previousTimeRef.current = next;
      setPlayheadMs(next);
      if (!isPlayingRef.current) return;
      if (next + PREFETCH_MARGIN_MS >= loadedUntilRef.current) {
        void loadWindow(loadedUntilRef.current);
      }
    }, 120);
    return () => {
      video.removeEventListener('seeked', onSeeked);
      window.clearInterval(timer);
    };
  }, [closeSocket, connectSocket, enabled, loadWindow, resetTimeline, video]);

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
    isPlaying,
    playbackRate,
    timelineRevision,
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
