import React, { memo, useCallback, useEffect, useRef, useState } from 'react';
import type { FC, PointerEvent as ReactPointerEvent } from 'react';
import DPlayer from 'dplayer';
import {
  CloseOutlined,
  CompressOutlined,
  ExpandOutlined,
  FastBackwardOutlined,
  FastForwardOutlined,
  FullscreenOutlined,
} from '@ant-design/icons';
import { Button, Dropdown, Space, Tooltip } from 'antd';
import type { MenuProps } from 'antd';

import {
  SEEK_STEP_SECONDS,
  type VideoPlayerProps,
  type VideoPlayerSize,
} from '@/base-ui/VideoPlayer/types';

import './style.less';

const SPEED_OPTIONS = [0.75, 1, 1.25, 1.5, 2];

const VideoPlayer: FC<VideoPlayerProps> = ({
  url,
  pic,
  title = '视频播放',
  autoplay = false,
  muted = false,
  loop = false,
  mode = 'inline',
  defaultSize = 'normal',
  className,
  onClose,
  onEnded,
  onError,
  overlay,
  onVideoReady,
}) => {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const playerRef = useRef<DPlayer | null>(null);
  const shellRef = useRef<HTMLDivElement | null>(null);
  const dragState = useRef<{
    active: boolean;
    startX: number;
    startY: number;
    originLeft: number;
    originTop: number;
  } | null>(null);

  const [size, setSize] = useState<VideoPlayerSize>(defaultSize);
  const [speed, setSpeed] = useState(1);
  const [offset, setOffset] = useState({ left: 24, top: 96 });

  const seekBy = useCallback((delta: number) => {
    const dp = playerRef.current;
    if (!dp?.video) return;
    const next = Math.max(
      0,
      Math.min(dp.video.duration || 0, dp.video.currentTime + delta),
    );
    dp.seek(next);
    const label = delta > 0 ? `前进 ${delta} 秒` : `后退 ${Math.abs(delta)} 秒`;
    dp.notice(label, 1200, 0.85);
  }, []);

  const setPlaybackSpeed = useCallback((rate: number) => {
    const dp = playerRef.current;
    if (!dp) return;
    dp.speed(rate);
    setSpeed(rate);
    dp.notice(`${rate}x`, 1000, 0.85);
  }, []);

  const requestFullscreen = useCallback(() => {
    playerRef.current?.fullScreen.request('browser');
  }, []);

  const toggleSize = useCallback(() => {
    setSize((prev) => (prev === 'mini' ? 'normal' : 'mini'));
  }, []);

  const onEndedRef = useRef(onEnded);
  const onErrorRef = useRef(onError);
  const onVideoReadyRef = useRef(onVideoReady);
  onEndedRef.current = onEnded;
  onErrorRef.current = onError;
  onVideoReadyRef.current = onVideoReady;

  useEffect(() => {
    if (!containerRef.current || !url) return undefined;

    const dp = new DPlayer({
      container: containerRef.current,
      autoplay,
      loop,
      theme: '#ff6600',
      lang: 'zh-cn',
      hotkey: true,
      preload: 'metadata',
      volume: muted ? 0 : 0.7,
      mutex: true,
      playbackSpeed: SPEED_OPTIONS,
      video: {
        url,
        pic,
        type: 'auto',
      },
    });

    playerRef.current = dp;
    if (muted && dp.video) {
      dp.video.muted = true;
    }
    if (loop && dp.video) {
      dp.video.loop = true;
    }
    onVideoReadyRef.current?.(dp.video);

    dp.on('ended', () => onEndedRef.current?.());
    dp.on('error', () => onErrorRef.current?.());

    return () => {
      onVideoReadyRef.current?.(null);
      playerRef.current = null;
      dp.destroy();
    };
  }, [url, pic, autoplay, muted, loop]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (!playerRef.current) return;
      const target = event.target as HTMLElement | null;
      if (
        target &&
        (target.tagName === 'INPUT' ||
          target.tagName === 'TEXTAREA' ||
          target.isContentEditable)
      ) {
        return;
      }
      if (event.key === 'ArrowLeft') {
        event.preventDefault();
        seekBy(-SEEK_STEP_SECONDS);
      } else if (event.key === 'ArrowRight') {
        event.preventDefault();
        seekBy(SEEK_STEP_SECONDS);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [seekBy]);

  const onDragStart = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (mode !== 'floating') return;
    if ((event.target as HTMLElement).closest('button, a, .ant-dropdown')) {
      return;
    }
    const shell = shellRef.current;
    if (!shell) return;
    event.currentTarget.setPointerCapture(event.pointerId);
    dragState.current = {
      active: true,
      startX: event.clientX,
      startY: event.clientY,
      originLeft: offset.left,
      originTop: offset.top,
    };
  };

  const onDragMove = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (!dragState.current?.active) return;
    const dx = event.clientX - dragState.current.startX;
    const dy = event.clientY - dragState.current.startY;
    const nextLeft = Math.max(
      8,
      Math.min(window.innerWidth - 120, dragState.current.originLeft + dx),
    );
    const nextTop = Math.max(
      8,
      Math.min(window.innerHeight - 80, dragState.current.originTop + dy),
    );
    setOffset({ left: nextLeft, top: nextTop });
  };

  const onDragEnd = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (!dragState.current?.active) return;
    dragState.current.active = false;
    try {
      event.currentTarget.releasePointerCapture(event.pointerId);
    } catch {
      /* ignore */
    }
  };

  const speedMenu: MenuProps['items'] = SPEED_OPTIONS.map((rate) => ({
    key: String(rate),
    label: `${rate}x`,
    onClick: () => setPlaybackSpeed(rate),
  }));

  const shellClass = [
    'video-player',
    `video-player--${mode}`,
    mode === 'floating' ? `video-player--${size}` : '',
    className || '',
  ]
    .filter(Boolean)
    .join(' ');

  const shellStyle =
    mode === 'floating' ? { left: offset.left, top: offset.top } : undefined;

  return (
    <div ref={shellRef} className={shellClass} style={shellStyle}>
      {mode === 'floating' && (
        <div
          className="video-player__chrome"
          onPointerDown={onDragStart}
          onPointerMove={onDragMove}
          onPointerUp={onDragEnd}
          onPointerCancel={onDragEnd}
        >
          <span className="video-player__title" title={title}>
            {title}
          </span>
          <Space size={4} className="video-player__chrome-actions">
            <Tooltip title={size === 'mini' ? '放大' : '缩小'}>
              <Button
                type="text"
                size="small"
                icon={
                  size === 'mini' ? <ExpandOutlined /> : <CompressOutlined />
                }
                onClick={toggleSize}
              />
            </Tooltip>
            <Tooltip title="全屏">
              <Button
                type="text"
                size="small"
                icon={<FullscreenOutlined />}
                onClick={requestFullscreen}
              />
            </Tooltip>
            {onClose && (
              <Tooltip title="关闭">
                <Button
                  type="text"
                  size="small"
                  icon={<CloseOutlined />}
                  onClick={onClose}
                />
              </Tooltip>
            )}
          </Space>
        </div>
      )}

      <div className="video-player__stage">
        <div ref={containerRef} className="video-player__dplayer" />
        {overlay}
      </div>

      <div className="video-player__toolbar">
        <Space size={8} wrap>
          <Tooltip title={`后退 ${SEEK_STEP_SECONDS} 秒（←）`}>
            <Button
              size="small"
              icon={<FastBackwardOutlined />}
              onClick={() => seekBy(-SEEK_STEP_SECONDS)}
            >
              -{SEEK_STEP_SECONDS}s
            </Button>
          </Tooltip>
          <Tooltip title={`前进 ${SEEK_STEP_SECONDS} 秒（→）`}>
            <Button
              size="small"
              icon={<FastForwardOutlined />}
              onClick={() => seekBy(SEEK_STEP_SECONDS)}
            >
              +{SEEK_STEP_SECONDS}s
            </Button>
          </Tooltip>
          <Dropdown menu={{ items: speedMenu, selectedKeys: [String(speed)] }}>
            <Button size="small">{speed}x</Button>
          </Dropdown>
          {mode === 'inline' && (
            <Button
              size="small"
              icon={<FullscreenOutlined />}
              onClick={requestFullscreen}
            >
              全屏
            </Button>
          )}
        </Space>
        <span className="video-player__hint">进度条可拖拽 · 空格播放/暂停</span>
      </div>
    </div>
  );
};

export type {
  VideoPlayerProps,
  VideoPlayerMode,
  VideoPlayerSize,
} from '@/base-ui/VideoPlayer/types';
export { SEEK_STEP_SECONDS } from '@/base-ui/VideoPlayer/types';
export default memo(VideoPlayer);
