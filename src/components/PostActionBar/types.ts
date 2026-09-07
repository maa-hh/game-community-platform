export interface IProps {
  viewCount: number;
  likeCount: number;
  favoriteCount: number;
  shareCount?: number;
  liked: boolean;
  favorited: boolean;
  likeDisabled?: boolean;
  favoriteDisabled?: boolean;
  onLike: () => void;
  onFavorite: () => void;
  onShare?: () => void;
}
