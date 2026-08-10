export interface CommentOpsProps {
  liked: boolean;
  likeCount: number;
  isMine?: boolean;
  size?: 'sm' | 'md';
  onLike: () => void;
  onReply: () => void;
  onReport: () => void;
  onDelete: () => void;
  deleteTitle?: string;
}
