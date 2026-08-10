import type { MouseEventHandler } from 'react';
import type { PostRefCard } from '@/types/post';

export interface IShareCardProps {
  data: PostRefCard;
  className?: string;
  /** 仅展示，不可点击跳转 */
  preview?: boolean;
  /** 封面已在页内其他位置展示时隐藏卡片内封面 */
  suppressCover?: boolean;
  onClick?: MouseEventHandler<HTMLElement>;
}
