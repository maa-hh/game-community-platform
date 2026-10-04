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
const FULLSCREEN_CLASS = 'video-player__dplayer--fullscreen';

interface LockableScreenOrientation {
  lock?: (orientation: 'landscape') => Promise<void>;
  unlock?: () => void;
}

interface ResizableDPlayer extends DPlayer {
  resize: () => void;
}

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
  const picRef = useRef(pic);
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
  const [volumeHost, setVolumeHost] = useState<HTMLElement | null>(null);
  const [volumeLevel, setVolumeLevel] = useState(0.7);
  const [volumeOpen, setVolumeOpen] = useState(false);

  picRef.current = pic;

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

  const setPlayerVolume = useCallback((value: number) => {
    const next = Math.max(0, Math.min(1, value));
    playerRef.current?.volume(next, false, true);
    setVolumeLevel(next);
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
      // 先创建再设置 muted，避免 DPlayer 在构造阶段抢先触发未静音自动播放。
      autoplay: false,
      loop: false,
      theme: '#ff6600',
      lang: 'zh-cn',
      hotkey: true,
      preload: 'metadata',
      // 静音交给 video.muted，保留实际音量；否则移动端点“取消静音”后仍会是 0。
      volume: 0.7,
      mutex: true,
      playbackSpeed: SPEED_OPTIONS,
      video: {
        url,
        pic: picRef.current,
        type: 'auto',
      },
    });

    playerRef.current = dp;
    const setPausedState = (paused: boolean) => {
      containerRef.current?.classList.toggle(
        'video-player__dplayer--paused',
        paused,
      );
    };

    if (!autoplay) {
      setPausedState(true);
    }
    const handlePlay = () => setPausedState(false);
    const handlePause = () => setPausedState(true);
    dp.video.addEventListener('play', handlePlay);
    dp.video.addEventListener('pause', handlePause);
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
    const volumeButton = controller?.querySelector<HTMLButtonElement>(
      '.dplayer-volume-icon',
    );
    const volumeControl = volumeButton?.closest<HTMLElement>('.dplayer-volume');
    const isMobilePlayer =
      containerRef.current.classList.contains('dplayer-mobile');
    let layoutFrame = 0;
    const settledLayoutTimers: number[] = [];
    let playerFullscreen = false;
    let disposed = false;

    const updateOverlayBounds = () => {
      const container = containerRef.current;
      if (!container) return;
      const containerWidth = container.clientWidth;
      const containerHeight = container.clientHeight;
      if (containerWidth <= 0 || containerHeight <= 0) return;

      const videoRatio =
        dp.video.videoWidth > 0 && dp.video.videoHeight > 0
          ? dp.video.videoWidth / dp.video.videoHeight
          : 16 / 9;
      const containerRatio = containerWidth / containerHeight;
      const mediaWidth =
        containerRatio > videoRatio
          ? containerHeight * videoRatio
          : containerWidth;
      const mediaHeight =
        containerRatio > videoRatio
          ? containerHeight
          : containerWidth / videoRatio;

      nextOverlayHost.style.left = `${(containerWidth - mediaWidth) / 2}px`;
      nextOverlayHost.style.top = `${(containerHeight - mediaHeight) / 2}px`;
      nextOverlayHost.style.width = `${mediaWidth}px`;
      nextOverlayHost.style.height = `${mediaHeight}px`;
    };

    const updateControlHostBounds = () => {
      if (!controller || !leftIcons || !rightIcons) return;
      const edgeGap = controller.clientWidth <= 440 ? 8 : 16;
      // offset* 使用播放器自身坐标；竖屏全屏兜底旋转后，getBoundingClientRect
      // 会返回旋转后的屏幕坐标，导致控制栏预留宽度计算错误。
      const controllerWidth = controller.clientWidth;
      const leftReserved = leftIcons.offsetLeft + leftIcons.offsetWidth;
      const rightReserved = controllerWidth - rightIcons.offsetLeft;
      const left = Math.max(edgeGap, leftReserved + edgeGap);
      const right = Math.max(edgeGap, rightReserved + edgeGap);
      const availableWidth = Math.max(
        0,
        controllerWidth - leftReserved - rightReserved - edgeGap * 2,
      );
      const widthScale = availableWidth / DEFAULT_CONTROL_AVAILABLE_WIDTH;
      const nativeIcon = controller.querySelector<HTMLElement>(
        '.dplayer-play-icon, .dplayer-full-icon',
      );
      const nativeIconHeight = nativeIcon?.offsetHeight || 38;
      const nativeScale = nativeIconHeight / 38;
      const controlScale = Math.max(0.72, Math.min(1, widthScale, nativeScale));
      host.style.setProperty('--vp-control-left', `${left}px`);
      host.style.setProperty('--vp-control-right', `${right}px`);
      host.style.setProperty('--vp-control-scale', String(controlScale));
    };

    const updateLayout = () => {
      updateControlHostBounds();
      updateOverlayBounds();
    };
    const scheduleLayout = () => {
      window.cancelAnimationFrame(layoutFrame);
      layoutFrame = window.requestAnimationFrame(updateLayout);
    };
    const getScreenOrientation = () =>
      (
        window.screen as Screen & {
          orientation?: LockableScreenOrientation;
        }
      ).orientation;
    const releaseOrientationLock = () => {
      try {
        getScreenOrientation()?.unlock?.();
      } catch {
        // 部分 WebView 声明了 API，但退出全屏时仍可能拒绝调用。
      }
    };
    const requestLandscapeOrientation = () => {
      if (
        !containerRef.current?.classList.contains('dplayer-mobile') ||
        !window.matchMedia('(orientation: portrait)').matches
      ) {
        return;
      }
      const orientation = getScreenOrientation();
      if (!orientation?.lock) return;
      void orientation
        .lock('landscape')
        .then(() => {
          if (disposed || !playerFullscreen) {
            try {
              orientation.unlock?.();
            } catch {
              // 页面已退出全屏，无需再处理不支持的 unlock。
            }
            return;
          }
          scheduleLayout();
        })
        .catch(() => {
          // 不支持方向锁定时由 CSS 将播放器旋转为横屏布局。
        });
    };
    const scheduleSettledLayout = () => {
      settledLayoutTimers.splice(0).forEach((timer) => {
        window.clearTimeout(timer);
      });
      const refresh = () => {
        if (disposed) return;
        (dp as ResizableDPlayer).resize();
        scheduleLayout();
      };
      refresh();
      [60, 180, 420].forEach((delay) => {
        settledLayoutTimers.push(window.setTimeout(refresh, delay));
      });
    };
    const setFullscreenState = (active: boolean) => {
      const stateChanged = playerFullscreen !== active;
      playerFullscreen = active;
      containerRef.current?.classList.toggle(FULLSCREEN_CLASS, active);
      if (!stateChanged) {
        scheduleLayout();
        return;
      }
      if (active) {
        requestLandscapeOrientation();
        scheduleLayout();
      } else {
        releaseOrientationLock();
        setVolumeOpen(false);
        scheduleSettledLayout();
      }
    };
    const handleDocumentFullscreenChange = () => {
      const fullscreenDocument = document as Document & {
        webkitFullscreenElement?: Element | null;
      };
      const fullscreenElement =
        document.fullscreenElement ??
        fullscreenDocument.webkitFullscreenElement ??
        null;
      const container = containerRef.current;
      const browserFullscreen = Boolean(
        fullscreenElement &&
        container &&
        (fullscreenElement === container ||
          container.contains(fullscreenElement) ||
          fullscreenElement.contains(container)),
      );
      const webFullscreen = Boolean(
        container?.classList.contains('dplayer-fulled'),
      );
      setFullscreenState(browserFullscreen || webFullscreen);
    };
    const updateVolumeLabel = () => {
      if (!volumeButton) return;
      const nextVolume = dp.video.muted ? 0 : dp.video.volume;
      setVolumeLevel(nextVolume);
      const label = isMobilePlayer
        ? `调节音量，当前 ${Math.round(nextVolume * 100)}%`
        : dp.video.muted || dp.video.volume === 0
          ? '取消静音'
          : '静音';
      volumeButton.setAttribute('aria-label', label);
      volumeButton.setAttribute('title', label);
    };
    const toggleMobileVolume = (event: MouseEvent) => {
      if (!isMobilePlayer) return;
      event.preventDefault();
      event.stopPropagation();
      event.stopImmediatePropagation();
      setVolumeOpen((open) => !open);
    };
    const closeMobileVolume = (event: PointerEvent) => {
      if (
        volumeControl &&
        event.target instanceof Node &&
        !volumeControl.contains(event.target)
      ) {
        setVolumeOpen(false);
      }
    };

    const resizeObserver =
      typeof ResizeObserver !== 'undefined'
        ? new ResizeObserver(scheduleLayout)
        : null;
    if (resizeObserver) {
      resizeObserver.observe(containerRef.current);
      if (controller) resizeObserver.observe(controller);
      if (leftIcons) resizeObserver.observe(leftIcons);
      if (rightIcons) resizeObserver.observe(rightIcons);
    }
    dp.video.addEventListener('loadedmetadata', scheduleLayout);
    dp.video.addEventListener('volumechange', updateVolumeLabel);
    window.addEventListener('resize', scheduleLayout);
    window.addEventListener('orientationchange', scheduleLayout);
    window.visualViewport?.addEventListener('resize', scheduleLayout);
    getScreenOrientation()?.addEventListener('change', scheduleSettledLayout);
    document.addEventListener('pointerdown', closeMobileVolume);
    document.addEventListener(
      'fullscreenchange',
      handleDocumentFullscreenChange,
    );
    document.addEventListener(
      'webkitfullscreenchange',
      handleDocumentFullscreenChange,
    );
    dp.on('fullscreen', () => setFullscreenState(true));
    dp.on('fullscreen_cancel', () => setFullscreenState(false));
    dp.events.on('webfullscreen', () => setFullscreenState(true));
    dp.events.on('webfullscreen_cancel', () => setFullscreenState(false));

    scheduleLayout();
    setControlHost(controller ? host : null);
    setOverlayHost(nextOverlayHost);
    setVolumeHost(isMobilePlayer && volumeControl ? volumeControl : null);
    if (muted && !dp.video.muted) {
      // 走 DPlayer 自己的静音逻辑，使图标、音量条和 video.muted 保持一致。
      volumeButton?.click();
      if (!volumeButton) dp.video.muted = true;
    }
    volumeButton?.addEventListener('click', toggleMobileVolume, true);
    updateVolumeLabel();
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

    if (autoplay) {
      dp.play();
    }

    return () => {
      disposed = true;
      releaseOrientationLock();
      window.cancelAnimationFrame(layoutFrame);
      settledLayoutTimers.forEach((timer) => window.clearTimeout(timer));
      setControlHost(null);
      setOverlayHost(null);
      setVolumeHost(null);
      setVolumeOpen(false);
      dp.video.removeEventListener('play', handlePlay);
      dp.video.removeEventListener('pause', handlePause);
      dp.video.removeEventListener('loadedmetadata', scheduleLayout);
      dp.video.removeEventListener('volumechange', updateVolumeLabel);
      window.removeEventListener('resize', scheduleLayout);
      window.removeEventListener('orientationchange', scheduleLayout);
      window.visualViewport?.removeEventListener('resize', scheduleLayout);
      getScreenOrientation()?.removeEventListener(
        'change',
        scheduleSettledLayout,
      );
      volumeButton?.removeEventListener('click', toggleMobileVolume, true);
      document.removeEventListener('pointerdown', closeMobileVolume);
      document.removeEventListener(
        'fullscreenchange',
        handleDocumentFullscreenChange,
      );
      document.removeEventListener(
        'webkitfullscreenchange',
        handleDocumentFullscreenChange,
      );
      resizeObserver?.disconnect();
      host.remove();
      nextOverlayHost.remove();
      onVideoReadyRef.current?.(null);
      playerRef.current = null;
      dp.destroy();
    };
  }, [url, autoplay, muted]);

  // 详情回填可能只补齐封面地址。更新 poster 即可，不能因此销毁并重建
  // DPlayer，否则正在播放的视频会回到 0 秒。
  useEffect(() => {
    const video = playerRef.current?.video;
    if (!video) return;
    video.poster = pic || '';
  }, [pic]);

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
      {volumeHost && volumeOpen
        ? createPortal(
            <div
              className="video-player__mobile-volume-panel"
              onClick={(event) => event.stopPropagation()}
              onPointerDown={(event) => event.stopPropagation()}
            >
              <input
                type="range"
                min={0}
                max={1}
                step={0.01}
                value={volumeLevel}
                aria-label="音量"
                onChange={(event) =>
                  setPlayerVolume(Number(event.target.value))
                }
              />
              <span>{Math.round(volumeLevel * 100)}%</span>
            </div>,
            volumeHost,
          )
        : null}
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
