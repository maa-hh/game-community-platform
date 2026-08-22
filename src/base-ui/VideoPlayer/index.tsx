import React, { memo, useCallback, useEffect, useRef, useState } from 'react';
import type { FC, PointerEvent as ReactPointerEvent } from 'react';
import { createPortal } from 'react-dom';
import DPlayer from 'dplayer';
import {
  CloseOutlined,
  CompressOutlined,
  ExpandOutlined,
} from '@ant-design/icons';
import { Button, Dropdown, Space, Switch, Tooltip } from 'antd';
import type { MenuProps } from 'antd';

import {
  SEEK_STEP_SECONDS,
  type VideoPlayerProps,
  type VideoPlayerSize,
} from '@/base-ui/VideoPlayer/types';

import './style.less';

const SPEED_OPTIONS = [0.5, 0.75, 1, 1.25, 1.5, 1.75, 2];
const DEFAULT_CONTROL_AVAILABLE_WIDTH = 581;

const getPlayerPopupContainer = (triggerNode: HTMLElement) =>
  triggerNode.closest<HTMLElement>('.video-player__dplayer') ?? document.body;

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
  controlContent,
  controlTrailingContent,
  onVideoReady,
}) => {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const playerRef = useRef<DPlayer | null>(null);
  const shellRef = useRef<HTMLDivElement | null>(null);
  const loopEnabledRef = useRef(loop);
  const dragState = useRef<{
    active: boolean;
    startX: number;
    startY: number;
    originLeft: number;
    originTop: number;
  } | null>(null);

  const [size, setSize] = useState<VideoPlayerSize>(defaultSize);
  const [speed, setSpeed] = useState(1);
  const [loopEnabled, setLoopEnabled] = useState(loop);
  const [offset, setOffset] = useState({ left: 24, top: 96 });
  const [controlHost, setControlHost] = useState<HTMLElement | null>(null);
  const [overlayHost, setOverlayHost] = useState<HTMLElement | null>(null);

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

  const toggleSize = useCallback(() => {
    setSize((prev) => (prev === 'mini' ? 'normal' : 'mini'));
  }, []);

  const setLoopPlayback = useCallback((enabled: boolean) => {
    loopEnabledRef.current = enabled;
    setLoopEnabled(enabled);
    if (playerRef.current?.video) {
      playerRef.current.video.loop = false;
    }
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
      loop: false,
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
    const controller = containerRef.current.querySelector<HTMLElement>(
      '.dplayer-controller',
    );
    const host = document.createElement('div');
    host.className = 'video-player__control-host';
    controller?.appendChild(host);
    const nextOverlayHost = document.createElement('div');
    nextOverlayHost.className = 'video-player__overlay-host';
    containerRef.current.appendChild(nextOverlayHost);

    const leftIcons = controller?.querySelector<HTMLElement>(
      '.dplayer-icons-left',
    );
    const rightIcons = controller?.querySelector<HTMLElement>(
      '.dplayer-icons-right',
    );
    const updateControlHostBounds = () => {
      if (!controller || !leftIcons || !rightIcons) return;
      const controllerRect = controller.getBoundingClientRect();
      const leftRect = leftIcons.getBoundingClientRect();
      const rightRect = rightIcons.getBoundingClientRect();
      const edgeGap = 16;
      const leftReserved = leftRect.right - controllerRect.left;
      const rightReserved = controllerRect.right - rightRect.left;
      const left = Math.max(edgeGap, leftReserved + edgeGap);
      const right = Math.max(edgeGap, rightReserved + edgeGap);
      const availableWidth = Math.max(
        0,
        controllerRect.width - leftReserved - rightReserved - edgeGap * 2,
      );
      const widthScale = availableWidth / DEFAULT_CONTROL_AVAILABLE_WIDTH;
      const nativeIcon = controller.querySelector<HTMLElement>(
        '.dplayer-play-icon, .dplayer-full-icon',
      );
      const nativeIconHeight = nativeIcon?.getBoundingClientRect().height || 38;
      const nativeScale = nativeIconHeight / 38;
      const controlScale = Math.max(0.72, Math.min(1, widthScale, nativeScale));
      host.style.setProperty('--vp-control-left', `${left}px`);
      host.style.setProperty('--vp-control-right', `${right}px`);
      host.style.setProperty('--vp-control-scale', String(controlScale));
    };
    updateControlHostBounds();
    const resizeObserver =
      typeof ResizeObserver !== 'undefined' &&
      controller &&
      leftIcons &&
      rightIcons
        ? new ResizeObserver(updateControlHostBounds)
        : null;
    if (resizeObserver && controller && leftIcons && rightIcons) {
      resizeObserver.observe(controller);
      resizeObserver.observe(leftIcons);
      resizeObserver.observe(rightIcons);
    }
    setControlHost(controller ? host : null);
    setOverlayHost(nextOverlayHost);
    if (muted && dp.video) {
      dp.video.muted = true;
    }
    if (dp.video) {
      dp.video.loop = false;
    }
    onVideoReadyRef.current?.(dp.video);

    dp.on('ended', () => {
      onEndedRef.current?.();
      if (!loopEnabledRef.current || !dp.video) return;
      dp.video.currentTime = 0;
      dp.play();
    });
    dp.on('error', () => onErrorRef.current?.());

    return () => {
      setControlHost(null);
      setOverlayHost(null);
      resizeObserver?.disconnect();
      host.remove();
      nextOverlayHost.remove();
      onVideoReadyRef.current?.(null);
      playerRef.current = null;
      dp.destroy();
    };
  }, [url, pic, autoplay, muted, loop]);

  useEffect(() => {
    loopEnabledRef.current = loop;
    setLoopEnabled(loop);
  }, [loop]);

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

  const controls = (
    <div
      className="video-player__control-row"
      onClick={(event) => event.stopPropagation()}
      onPointerDown={(event) => event.stopPropagation()}
    >
      {controlContent ? (
        <div className="video-player__control-custom">{controlContent}</div>
      ) : null}
      {controlTrailingContent ? (
        <div className="video-player__control-trailing">
          {controlTrailingContent}
        </div>
      ) : null}
      <Dropdown
        menu={{ items: speedMenu, selectedKeys: [String(speed)] }}
        trigger={['click']}
        classNames={{ root: 'video-player__speed-dropdown' }}
        getPopupContainer={getPlayerPopupContainer}
        placement="topRight"
      >
        <Button
          size="small"
          className="video-player__control-speed video-player__control-trigger"
        >
          {speed}x
        </Button>
      </Dropdown>
      <Space size={4} className="video-player__loop-control">
        <Switch size="small" checked={loopEnabled} onChange={setLoopPlayback} />
        <span>循环</span>
      </Space>
    </div>
  );

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
      </div>
      {overlayHost && overlay ? createPortal(overlay, overlayHost) : null}
      {controlHost ? createPortal(controls, controlHost) : null}
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
