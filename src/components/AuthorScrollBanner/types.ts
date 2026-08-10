import type { PostAuthor } from '@/types/post';

export interface IProps {
  visible: boolean;
  author: PostAuthor;
  followed: boolean;
  isOwner: boolean;
  onFollow: () => void;
  onShare: () => void;
}
