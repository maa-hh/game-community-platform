import type { DanmakuMessage } from '@/service/danmaku';

export type DanmakuDensity = 'low' | 'medium' | 'high';

export interface DanmakuPlayerProps {
  videoPublicId: string;
  url: string;
  pic?: string;
  title?: string;
  muted?: boolean;
  onReport?: (messageId: string) => void;
}

export interface DanmakuOverlayProps {
  playheadMs: number;
  messages: DanmakuMessage[];
  pendingMessages: DanmakuMessage[];
  enabled: boolean;
  density: DanmakuDensity;
  speed: number;
  onReport?: (messageId: string) => void;
}
