export interface CommentOpsProps {
  liked: boolean;
  likeCount: number;
  likePending?: boolean;
  isMine?: boolean;
  size?: 'sm' | 'md';
  onLike: () => void;
  onReply: () => void;
  onReport?: () => void;
  onDelete?: () => void;
  deleteTitle?: string;
}
