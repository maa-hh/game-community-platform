export interface IPostBottomBarProps {
  likeCount: number;
  favoriteCount: number;
  shareCount: number;
  liked: boolean;
  favorited: boolean;
  submitting?: boolean;
  className?: string;
  onLike: () => void;
  onFavorite: () => void;
  onShare: () => void;
  onComment: (content: string) => void;
  onFocusComment?: () => void;
}
