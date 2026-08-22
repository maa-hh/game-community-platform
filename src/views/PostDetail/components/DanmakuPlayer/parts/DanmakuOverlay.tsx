import React, { memo, useEffect, useRef, useState } from 'react';
import type { FC } from 'react';
import { Button, Tooltip } from 'antd';
import { FlagOutlined } from '@ant-design/icons';

import type { DanmakuMessage } from '@/service/danmaku';

import type { DanmakuOverlayProps } from '../types';

interface ActiveDanmaku extends DanmakuMessage {
  lane: number;
}

const BASE_DURATION_SECONDS = 11;
const MIN_DURATION_SECONDS = BASE_DURATION_SECONDS / 4;

const LANE_CONFIG = {
  low: { lanes: 4, maxPerTick: 4 },
  medium: { lanes: 6, maxPerTick: 7 },
  high: { lanes: 9, maxPerTick: 10 },
} as const;

const DanmakuOverlay: FC<DanmakuOverlayProps> = ({
  playheadMs,
  isPlaying,
  timelineRevision,
  reportResetKey,
  messages,
  pendingMessages,
  enabled,
  density,
  speed,
  onReport,
}) => {
  const [active, setActive] = useState<ActiveDanmaku[]>([]);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const scheduledRef = useRef(new Set<string>());
  const laneAvailableRef = useRef<number[]>([]);
  const activeRef = useRef<ActiveDanmaku[]>([]);
  activeRef.current = active;

  useEffect(() => {
    setActive([]);
    setSelectedIds(new Set());
    scheduledRef.current.clear();
    laneAvailableRef.current = [];
  }, [timelineRevision]);

  useEffect(() => {
    if (reportResetKey == null) return;
    setSelectedIds(new Set());
  }, [reportResetKey]);

  useEffect(() => {
    if (!enabled) {
      setActive([]);
      setSelectedIds(new Set());
      scheduledRef.current.clear();
      laneAvailableRef.current = [];
      return;
    }
    if (!isPlaying) return;

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
      laneAvailableRef.current[lane] = now + Math.max(0.6, 2.4 / speed) * 1000;
      scheduledRef.current.add(item.id);
      next.push({ ...item, lane });
    }
    if (next.length > 0) {
      setActive((current) => [...current, ...next]);
    }
  }, [
    density,
    enabled,
    isPlaying,
    messages,
    playheadMs,
    speed,
    timelineRevision,
  ]);

  useEffect(() => {
    const activeIds = new Set(messages.map((item) => item.id));
    scheduledRef.current.forEach((id) => {
      if (!activeIds.has(id)) scheduledRef.current.delete(id);
    });
  }, [messages]);

  useEffect(() => {
    setSelectedIds((current) => {
      const activeIds = new Set(active.map((item) => item.id));
      const next = new Set<string>();
      current.forEach((itemId) => {
        if (activeIds.has(itemId)) next.add(itemId);
      });
      return next.size === current.size ? current : next;
    });
  }, [active]);

  const removeActive = (id: string) => {
    setActive((current) => current.filter((item) => item.id !== id));
    setSelectedIds((current) => {
      if (!current.has(id)) return current;
      const next = new Set(current);
      next.delete(id);
      return next;
    });
  };

  if (!enabled) return null;

  return (
    <div className="danmaku-overlay">
      {pendingMessages.length > 0 ? (
        <div className="danmaku-overlay__mine-preview">
          {pendingMessages[pendingMessages.length - 1].content}
        </div>
      ) : null}
      {active.map((item) => {
        const canReport = Boolean(onReport) && !item.isMine;

        return (
          <div
            key={item.id}
            className={`danmaku-overlay__track${selectedIds.has(item.id) ? ' is-selected' : ''}`}
            style={{
              top: `${8 + (item.lane * 82) / LANE_CONFIG[density].lanes}%`,
              animationDuration: `${Math.max(
                MIN_DURATION_SECONDS,
                BASE_DURATION_SECONDS / speed,
              )}s`,
              animationPlayState:
                isPlaying && !selectedIds.has(item.id) ? 'running' : 'paused',
            }}
            onAnimationEnd={(event) => {
              if (
                event.target !== event.currentTarget ||
                event.animationName !== 'danmaku-slide' ||
                selectedIds.has(item.id)
              ) {
                return;
              }
              removeActive(item.id);
            }}
          >
            <button
              type="button"
              className={`danmaku-overlay__item${item.isMine ? ' is-mine' : ''}`}
              onClick={
                canReport
                  ? () => {
                      setSelectedIds((current) => {
                        const next = new Set(current);
                        if (next.has(item.id)) {
                          next.delete(item.id);
                        } else {
                          next.add(item.id);
                        }
                        return next;
                      });
                    }
                  : undefined
              }
              title={
                canReport
                  ? selectedIds.has(item.id)
                    ? '再次点击释放弹幕'
                    : '点击暂停弹幕并显示举报按钮'
                  : undefined
              }
            >
              {item.content}
            </button>
            {selectedIds.has(item.id) && canReport ? (
              <Tooltip title="举报弹幕">
                <Button
                  type="text"
                  size="small"
                  className="danmaku-overlay__report"
                  icon={<FlagOutlined />}
                  aria-label="举报弹幕"
                  onClick={(event) => {
                    event.stopPropagation();
                    onReport?.(item.id);
                  }}
                />
              </Tooltip>
            ) : null}
          </div>
        );
      })}
    </div>
  );
};

export default memo(DanmakuOverlay);
