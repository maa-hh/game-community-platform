import type { ReactNode } from 'react';

export type VideoPlayerMode = 'inline' | 'floating';

export type VideoPlayerSize = 'normal' | 'mini';

export interface VideoPlayerProps {
  /** 视频地址（公网 URL / Presigned / blob:） */
  url: string;
  /** 封面图 */
  pic?: string;
  /** 顶栏标题（浮动模式） */
  title?: string;
  /** 自动播放（移动端可能被拦截） */
  autoplay?: boolean;
  /** 静音起播（详情视频小窗常用） */
  muted?: boolean;
  /** 循环播放，默认 false */
  loop?: boolean;
  /**
   * inline：占位 16:9 自适应宽度
   * floating：可拖动浮层，支持缩小/放大
   */
  mode?: VideoPlayerMode;
  /** 浮动模式下的初始尺寸 */
  defaultSize?: VideoPlayerSize;
  className?: string;
  /** 叠加在视频画面上的业务层内容，例如弹幕轨道。 */
  overlay?: ReactNode;
  /** DPlayer 创建真实 video 元素后回调，便于同步播放时间轴。 */
  onVideoReady?: (video: HTMLVideoElement | null) => void;
  /** 关闭回调（浮动模式显示关闭按钮） */
  onClose?: () => void;
  /** 播放结束 */
  onEnded?: () => void;
  /** 出错 */
  onError?: () => void;
}

/** 快进/快退秒数 */
export const SEEK_STEP_SECONDS = 5;
