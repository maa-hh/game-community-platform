import type { ContentCardData } from '@/types/content';
import type {
  ActivityActor,
  ActivityParentQuote,
  ActivitySubjectQuote,
} from '@/utils/profileActivity';

export type MainTabKey =
  'posts' | 'history' | 'liked' | 'received' | 'favorites' | 'comments';

export type ProfileStatKey = 'following' | 'followers' | 'likes' | 'favorites';

export type PostSubTabKey = 'published' | 'draft';

export type OtherProfileTabKey = 'posts' | 'steam';

export interface ProfileStats {
  following: number;
  followers: number;
  likes: number;
  favorites: number;
}

export interface FeedItemData extends ContentCardData {
  status?: PostSubTabKey;
  articleStatus?: number;
  viewCount?: number;
  targetArticleId?: string;
  commentId?: string;
  replyId?: string;
  parentQuote?: ActivityParentQuote;
  /** 活动流：comment | reply */
  activityType?: 'comment' | 'reply';
  /** 活动流：我的评论/回复或被赞/赞过的评论正文 */
  activityQuote?: string;
  activityActor?: ActivityActor;
  activitySubject?: ActivitySubjectQuote;
  /** @deprecated 活动流不再展示本人头像行 */
  activityShowAuthor?: boolean;
  /** 活动流：是否展示原帖 ShareCard（赞过/获赞的评论项为 false） */
  activityShowRefPost?: boolean;
  /** 游标分页用 */
  sortTime?: string;
  shareCount?: number;
  /** @deprecated 使用 images */
  cover?: string;
}

export const PROFILE_ACTIVITY_TABS: MainTabKey[] = ['comments'];
