export interface IPostBottomBarProps {
  likeCount: number;
  favoriteCount: number;
  shareCount: number;
  liked: boolean;
  favorited: boolean;
  likeDisabled?: boolean;
  favoriteDisabled?: boolean;
  submitting?: boolean;
  className?: string;
  onLike: () => void;
  onFavorite: () => void;
  onShare: () => void;
  onComment: (content: string) => void;
}
