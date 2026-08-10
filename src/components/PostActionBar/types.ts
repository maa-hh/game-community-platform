export interface IProps {
  viewCount: number;
  likeCount: number;
  favoriteCount: number;
  shareCount?: number;
  liked: boolean;
  favorited: boolean;
  onLike: () => void;
  onFavorite: () => void;
  onShare?: () => void;
}
