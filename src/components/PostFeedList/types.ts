import type { LatestPostItem } from '@/types/post';

import type {
  FeedPanelListLayout,
  IFeedPanelInfiniteProps,
} from '@/components/FeedPanel/types';

export interface IPostFeedListProps {
  items: LatestPostItem[];
  loading?: boolean;
  refreshing?: boolean;
  emptyText?: string;
  onRefresh?: () => void | Promise<void>;
  onItemClick: (item: LatestPostItem) => void;
  onLikeClick?: (item: LatestPostItem) => void;
  onFavoriteClick?: (item: LatestPostItem) => void;
  infinite?: IFeedPanelInfiniteProps;
  /** 展示热榜名次与热度 */
  showRank?: boolean;
  /** 列表布局：stack 纵向；masonry 按行轮询分列；hotRank 热榜行卡 */
  layout?: FeedPanelListLayout;
  /** 透传给 FeedPanel 的样式类名 */
  panelClassName?: string;
}
