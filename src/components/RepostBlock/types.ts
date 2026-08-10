import type { PostRefCard } from '@/types/post';

export interface IProps {
  /** 转发附言 / 个人评论 */
  quote?: string;
  refPost: PostRefCard;
  className?: string;
  /** 封面已在页内其他位置展示时隐藏引用卡封面 */
  suppressCover?: boolean;
}
