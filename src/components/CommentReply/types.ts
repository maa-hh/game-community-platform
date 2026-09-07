import type { PostReply } from '@/types/post';
import type { IUserDecoration } from '@/types/cosmetic';

export interface IProps {
  reply: PostReply;
  isMine: boolean;
  onLike: () => void;
  onReply: () => void;
  onReport?: () => void;
  onDelete?: () => void;
  decoration?: IUserDecoration;
}
