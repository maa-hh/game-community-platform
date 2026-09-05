import type { ReactNode, RefObject } from 'react';

export type FeedPanelListLayout = 'stack' | 'masonry' | 'hotRank';

export interface IFeedPanelInfiniteProps {
  sentinelRef?: RefObject<HTMLDivElement | null>;
  loadingMore?: boolean;
  hasMore?: boolean;
  /** 已有列表条数，用于控制底部提示不与空态冲突 */
  itemCount?: number;
}

export interface IFeedPanelProps {
  children?: ReactNode;
  /** 瀑布流由调用方提供，避免布局容器持有具体实现。 */
  masonry?: ReactNode;
  /** 首屏加载中（列表为空时） */
  loading?: boolean;
  /** 点击刷新 */
  onRefresh?: () => void | Promise<void>;
  className?: string;
  /** 无数据时的占位（仍在大容器内） */
  empty?: ReactNode;
  /** 触底加载 */
  infinite?: IFeedPanelInfiniteProps;
  /** 列表布局：stack 纵向堆叠；masonry 按行轮询分列瀑布流 */
  listLayout?: FeedPanelListLayout;
}
