import type { DanmakuMessage } from '@/service/danmaku';

export type DanmakuDensity = 'low' | 'medium' | 'high';

export interface DanmakuPlayerProps {
  videoPublicId: string;
  targetDanmakuId?: number;
  url: string;
  pic?: string;
  title?: string;
  muted?: boolean;
  onReport?: (messageId: string) => void;
  reportResetKey?: number;
}

export interface DanmakuOverlayProps {
  playheadMs: number;
  isPlaying: boolean;
  timelineRevision: number;
  reportResetKey?: number;
  messages: DanmakuMessage[];
  pendingMessages: DanmakuMessage[];
  enabled: boolean;
  density: DanmakuDensity;
  speed: number;
  onReport?: (messageId: string) => void;
}
