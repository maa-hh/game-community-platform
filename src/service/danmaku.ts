import hyRequest from '@/service/request';
import { BASE_URL } from '@/service/config';
import { getAccessToken } from '@/utils/storage';
import type { IDataType } from '@/service/types';

export interface DanmakuMessage {
  id: string;
  eventId?: string;
  clientMessageId?: string;
  videoPublicId: string;
  videoTimeMs: number;
  displayTimeMs: number;
  seq: number;
  accountId: number;
  username?: string;
  avatar?: string;
  content: string;
  status?: number;
  isMine?: boolean;
  pending?: boolean;
}

export interface DanmakuSendPayload {
  type: 'send';
  clientMessageId: string;
  videoTimeMs: number;
  content: string;
}

export function fetchDanmakuHistoryApi(
  videoPublicId: string,
  fromMs: number,
  toMs: number,
  limit = 300,
) {
  return hyRequest.get<IDataType<DanmakuMessage[]>>({
    url: `/danmaku/history/${encodeURIComponent(videoPublicId)}`,
    params: { fromMs, toMs, limit },
  });
}

export function buildDanmakuWebSocketUrl(videoPublicId: string): string {
  const base = new URL(
    BASE_URL || window.location.origin,
    window.location.origin,
  );
  base.protocol = base.protocol === 'https:' ? 'wss:' : 'ws:';
  base.pathname = `${base.pathname.replace(/\/$/, '')}/danmaku/ws/${encodeURIComponent(videoPublicId)}`;
  base.search = '';
  const token = getAccessToken();
  if (token) {
    base.searchParams.set('accessToken', token);
  }
  return base.toString();
}
