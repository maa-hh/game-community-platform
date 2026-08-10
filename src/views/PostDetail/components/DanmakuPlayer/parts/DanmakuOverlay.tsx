import React, { memo, useEffect, useRef, useState } from 'react';
import type { FC } from 'react';

import type { DanmakuMessage } from '@/service/danmaku';

import type { DanmakuOverlayProps } from '../types';

interface ActiveDanmaku extends DanmakuMessage {
  lane: number;
  duration: number;
}

const LANE_CONFIG = {
  low: { lanes: 4, maxPerTick: 4 },
  medium: { lanes: 6, maxPerTick: 7 },
  high: { lanes: 9, maxPerTick: 10 },
} as const;

const DanmakuOverlay: FC<DanmakuOverlayProps> = ({
  playheadMs,
  messages,
  pendingMessages,
  enabled,
  density,
  speed,
  onReport,
}) => {
  const [active, setActive] = useState<ActiveDanmaku[]>([]);
  const scheduledRef = useRef(new Set<string>());
  const laneAvailableRef = useRef<number[]>([]);
  const activeRef = useRef<ActiveDanmaku[]>([]);
  activeRef.current = active;

  useEffect(() => {
    if (!enabled) {
      setActive([]);
      scheduledRef.current.clear();
      laneAvailableRef.current = [];
      return;
    }
    const config = LANE_CONFIG[density];
    const now = performance.now();
    if (laneAvailableRef.current.length !== config.lanes) {
      laneAvailableRef.current = Array.from(
        { length: config.lanes },
        () => now,
      );
    }
    const due = messages
      .filter((item) => !item.pending)
      .filter((item) => !scheduledRef.current.has(item.id))
      .filter((item) => item.displayTimeMs <= playheadMs + 120)
      .filter((item) => item.displayTimeMs >= playheadMs - 2200)
      .sort((a, b) => Number(b.isMine) - Number(a.isMine) || a.seq - b.seq);

    const next: ActiveDanmaku[] = [];
    for (const item of due) {
      if (next.length >= config.maxPerTick) break;
      const lane = laneAvailableRef.current.findIndex(
        (availableAt) => availableAt <= now,
      );
      if (lane < 0) break;
      const duration = Math.max(5, 11 / speed);
      laneAvailableRef.current[lane] = now + Math.max(1.2, 2.4 / speed) * 1000;
      scheduledRef.current.add(item.id);
      next.push({ ...item, lane, duration });
    }
    if (next.length > 0) {
      setActive((current) => [...current, ...next]);
    }
  }, [density, enabled, messages, playheadMs, speed]);

  useEffect(() => {
    const activeIds = new Set(messages.map((item) => item.id));
    scheduledRef.current.forEach((id) => {
      if (!activeIds.has(id)) scheduledRef.current.delete(id);
    });
  }, [messages]);

  const removeActive = (id: string) => {
    setActive((current) => current.filter((item) => item.id !== id));
  };

  if (!enabled) return null;

  return (
    <div className="danmaku-overlay">
      {pendingMessages.length > 0 ? (
        <div className="danmaku-overlay__mine-preview">
          {pendingMessages[pendingMessages.length - 1].content}
        </div>
      ) : null}
      {active.map((item) => (
        <button
          key={item.id}
          type="button"
          className={`danmaku-overlay__item${item.isMine ? ' is-mine' : ''}`}
          style={{
            top: `${8 + (item.lane * 82) / LANE_CONFIG[density].lanes}%`,
            animationDuration: `${item.duration}s`,
          }}
          onAnimationEnd={() => removeActive(item.id)}
          onClick={() => onReport?.(item.id)}
          title={onReport ? '点击举报弹幕' : undefined}
        >
          {item.content}
        </button>
      ))}
    </div>
  );
};

export default memo(DanmakuOverlay);
